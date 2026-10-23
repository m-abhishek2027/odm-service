package com.odm.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odm.dto.AssetLookupKey;
import com.odm.dto.AssetLookupResponse;
import com.odm.dto.AssetMetadataItem;
import com.odm.dto.LookupItemChecks;
import com.odm.dto.LookupResult;
import com.odm.dto.LookupStatus;
import com.odm.entity.Product;
import com.odm.exception.InvalidLookupRequestException;
import com.odm.lookup.LookupKey;
import com.odm.repo.ProductRepository;
import com.odm.service.AssetLookupService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Batch lookup with one result per requested item. All valid items are resolved with a single query on
 * lookup_key_hash; every row it returns is re-checked field by field, so only exact key matches count.
 * Data is returned only when exactly one active asset has the key; with two or more the item is DUPLICATE
 * and carries no data, so a wrong asset's metadata can never reach AEM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLookupServiceImpl implements AssetLookupService {

	private static final int MAX_LOGGED_KEYS = 20;

	private final ProductRepository productRepo;

	private final Validator validator;

	@Override
	@Transactional(readOnly = true, timeoutString = "${odm.lookup.timeout-seconds:20}")
	public AssetLookupResponse lookup(String requestId, List<AssetLookupKey> items) {
		long start = System.currentTimeMillis();
		rejectDuplicateReferenceIds(items);

		// validate each item on its own; a valid item gets its normalized key
		List<LookupKey> keys = new ArrayList<>(items.size());
		Map<Integer, Map<String, String>> invalid = new HashMap<>();
		for (int i = 0; i < items.size(); i++) {
			AssetLookupKey item = items.get(i);
			Map<String, String> errors = validate(item);
			if (errors.isEmpty()) {
				keys.add(LookupKey.of(item.getSource(), item.getSupplier(), item.getBrand(), item.getFileName()));
			} else {
				keys.add(null);
				invalid.put(i, errors);
			}
		}

		Set<String> hashes = new HashSet<>();
		keys.stream().filter(Objects::nonNull).forEach(k -> hashes.add(k.hash()));
		Map<String, List<Product>> matchesByHash = hashes.isEmpty() ? Map.of() : findMatches(hashes);

		// classify every item; a FOUND item's asset is attached below, once its collections are loaded
		LookupResult[] outcome = new LookupResult[items.size()];
		Product[] chosen = new Product[items.size()];
		for (int i = 0; i < items.size(); i++) {
			String referenceId = items.get(i).getReferenceId();
			LookupKey key = keys.get(i);
			if (key == null) {
				outcome[i] = LookupResult.invalid(referenceId, invalid.get(i));
				continue;
			}
			List<Product> matches = matchesByHash.getOrDefault(key.hash(), List.of());
			List<Product> active = matches.stream().filter(p -> Boolean.TRUE.equals(p.getActive())).toList();
			if (active.isEmpty()) {
				outcome[i] = matches.isEmpty() ? LookupResult.notFound(referenceId) : LookupResult.deleted(referenceId);
			} else if (active.size() == 1) {
				chosen[i] = active.get(0);
				outcome[i] = LookupResult.found(referenceId, null);
			} else {
				outcome[i] = LookupResult.duplicate(referenceId, active.size());
			}
		}

		// lazy collections only for the assets returned, inside the transaction (open-in-view is off)
		loadCollections(Arrays.stream(chosen).filter(Objects::nonNull).toList());
		List<LookupResult> results = new ArrayList<>(items.size());
		for (int i = 0; i < items.size(); i++) {
			results.add(chosen[i] == null ? outcome[i]
					: LookupResult.found(outcome[i].referenceId(), AssetMetadataItem.from(chosen[i])));
		}

		AssetLookupResponse.Summary summary = summarize(results);
		String status = summary.found() == summary.requested() ? AssetLookupResponse.SUCCESS
				: summary.found() == 0 ? AssetLookupResponse.NONE_FOUND : AssetLookupResponse.PARTIAL;
		log.info("lookup requestId={} requested={} found={} notFound={} duplicate={} deleted={} invalid={} durationMs={}",
				requestId, summary.requested(), summary.found(), summary.notFound(), summary.duplicate(),
				summary.deleted(), summary.invalid(), System.currentTimeMillis() - start);
		logForDataTeam(requestId, items, results);
		return new AssetLookupResponse(requestId, Instant.now(), status, summary, results);
	}

	private void rejectDuplicateReferenceIds(List<AssetLookupKey> items) {
		Map<String, Integer> firstIndex = new HashMap<>();
		Map<String, String> errors = new LinkedHashMap<>();
		for (int i = 0; i < items.size(); i++) {
			Integer first = firstIndex.putIfAbsent(items.get(i).getReferenceId(), i);
			if (first != null) {
				errors.put("assets[" + i + "].referenceId", "duplicate referenceId, also at assets[" + first + "]");
			}
		}
		if (!errors.isEmpty()) {
			throw new InvalidLookupRequestException(errors);
		}
	}

	private Map<String, String> validate(AssetLookupKey item) {
		Map<String, String> errors = new TreeMap<>();
		for (ConstraintViolation<AssetLookupKey> violation : validator.validate(item, LookupItemChecks.class)) {
			errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
		}
		return errors;
	}

	// one query for the whole batch; rows are grouped by the hash recomputed from their own fields, so a
	// stale stored hash or a (theoretical) collision can never attach a row to the wrong key
	private Map<String, List<Product>> findMatches(Set<String> hashes) {
		Map<String, List<Product>> byHash = new HashMap<>();
		for (Product product : productRepo.findByLookupKeyHashIn(hashes)) {
			String hash = LookupKey.hashOf(product.getSource(), product.getSupplier(), product.getBrand(),
					product.getAssetName());
			if (hash != null && hashes.contains(hash)) {
				byHash.computeIfAbsent(hash, h -> new ArrayList<>()).add(product);
			} else {
				log.warn("Asset {} has a stale lookup_key_hash; it is ignored until the hash is recomputed",
						product.getUuid());
			}
		}
		return byHash;
	}

	private static AssetLookupResponse.Summary summarize(List<LookupResult> results) {
		int found = 0, notFound = 0, duplicate = 0, deleted = 0, invalid = 0;
		for (LookupResult r : results) {
			switch (r.status()) {
				case FOUND -> found++;
				case NOT_FOUND -> notFound++;
				case DUPLICATE -> duplicate++;
				case DELETED -> deleted++;
				case INVALID -> invalid++;
			}
		}
		return new AssetLookupResponse.Summary(results.size(), found, notFound, duplicate, deleted, invalid);
	}

	// DUPLICATE and DELETED need a person, so their keys are logged for the data team (a few per request)
	private static void logForDataTeam(String requestId, List<AssetLookupKey> items, List<LookupResult> results) {
		int logged = 0;
		for (int i = 0; i < results.size() && logged < MAX_LOGGED_KEYS; i++) {
			LookupStatus status = results.get(i).status();
			if (status == LookupStatus.DUPLICATE || status == LookupStatus.DELETED) {
				AssetLookupKey item = items.get(i);
				log.warn("lookup requestId={} referenceId={} status={} matchCount={} key=[{} | {} | {} | {}]",
						requestId, item.getReferenceId(), status, results.get(i).matchCount(), item.getSource(),
						item.getSupplier(), item.getBrand(), item.getFileName());
				logged++;
			}
		}
	}

	private static void loadCollections(List<Product> products) {
		for (Product product : products) {
			Hibernate.initialize(product.getLanguage());
			Hibernate.initialize(product.getProductRegion());
			Hibernate.initialize(product.getChannelName());
			Hibernate.initialize(product.getProductBrandID());
			Hibernate.initialize(product.getProductSubBrand());
			Hibernate.initialize(product.getProductLine());
			Hibernate.initialize(product.getProductCategory());
			Hibernate.initialize(product.getProductType());
		}
	}

}
