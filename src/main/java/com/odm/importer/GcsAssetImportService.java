package com.odm.importer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.odm.dto.ImportReport;
import com.odm.entity.Product;
import com.odm.exception.ImportInProgressException;
import com.odm.lookup.LookupKey;
import com.odm.repo.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates one asset per object found under a bucket prefix. Only fields derivable from the path are set
 * (source, supplier, brand, assetType, file name, title, url); the scorecard fields are left to AEM.
 * Objects whose gs:// url is already stored are skipped, so the import can be re-run safely; for those, a missing
 * content MD5 is filled in from the bucket (lookup uses it to tell identical copies from different files).
 * Rows are written with plain JDBC batches (one multi-row INSERT per batch with rewriteBatchedStatements),
 * since a JPA persist per row costs a full round trip to Cloud SQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GcsAssetImportService {

	static final int BATCH_SIZE = 1000;

	// columns set by the import; keep in sync with the Product mapping
	static final String INSERT_SQL = "INSERT INTO products (uuid, title, source, supplier, brand, asset_type, "
			+ "asset_name, orig_asset_name, asset_url, active, created_on, created_by, modified_on, modified_by, "
			+ "lookup_key_hash, content_md5) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	static final String MD5_UPDATE_SQL = "UPDATE products SET content_md5 = ? WHERE asset_url = ? AND content_md5 IS NULL";

	private static final int MAX_SAMPLES = 20;

	private final Storage storage;

	private final ProductRepository productRepo;

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	private final AuditorAware<String> auditorAware;

	private final AtomicBoolean running = new AtomicBoolean();

	public ImportReport importAssets(String bucket, String prefix, boolean dryRun) {
		if (!running.compareAndSet(false, true)) {
			throw new ImportInProgressException("An import is already running; try again when it has finished");
		}
		try {
			return doImport(bucket, prefix, dryRun);
		} finally {
			running.set(false);
		}
	}

	private ImportReport doImport(String bucket, String prefix, boolean dryRun) {
		long start = System.currentTimeMillis();
		String auditor = auditorAware.getCurrentAuditor().orElse("system");
		String urlPrefix = "gs://" + bucket + "/" + prefix;
		Set<String> existingUrls = new HashSet<>();
		Set<String> missingMd5Urls = new HashSet<>();
		for (ProductRepository.StoredObject stored : productRepo.findStoredObjectsLike(urlPrefix + "%")) {
			String url = stored.getAssetUrl();
			if (url != null && url.startsWith(urlPrefix)) {
				existingUrls.add(url);
				if (stored.getContentMd5() == null) {
					missingMd5Urls.add(url);
				}
			}
		}
		log.info("Import gs://{}/{} started (dryRun={}), {} assets already stored", bucket, prefix, dryRun,
				existingUrls.size());

		long listed = 0, folders = 0, systemFiles = 0, invalid = 0, already = 0;
		List<String> skippedSamples = new ArrayList<>();
		List<Product> pending = new ArrayList<>();
		List<Object[]> md5Updates = new ArrayList<>();

		Page<Blob> page = storage.list(bucket, Storage.BlobListOption.prefix(prefix),
				Storage.BlobListOption.pageSize(1000));
		for (Blob blob : page.iterateAll()) {
			listed++;
			String name = blob.getName();
			if (name.endsWith("/")) {
				folders++;
				continue;
			}
			String fileName = name.substring(name.lastIndexOf('/') + 1);
			if (GcsAssetPath.isSystemFile(fileName)) {
				systemFiles++;
				addSample(skippedSamples, "system file: " + name);
				continue;
			}
			var path = GcsAssetPath.parse(name, prefix, blob.getContentType());
			if (path.isEmpty()) {
				invalid++;
				addSample(skippedSamples, "unexpected path: " + name);
				continue;
			}
			String url = "gs://" + bucket + "/" + name;
			if (existingUrls.contains(url)) {
				already++;
				if (missingMd5Urls.contains(url) && blob.getMd5() != null) {
					md5Updates.add(new Object[] { blob.getMd5(), url });
				}
				continue;
			}
			pending.add(toProduct(path.get(), url, blob.getMd5()));
		}

		Map<String, Long> byBrand = new TreeMap<>();
		Map<String, Long> byAssetType = new TreeMap<>();
		long imported = 0, failed = 0;
		List<String> errors = new ArrayList<>();

		for (int from = 0; from < pending.size(); from += BATCH_SIZE) {
			List<Product> batch = pending.subList(from, Math.min(from + BATCH_SIZE, pending.size()));
			if (!dryRun) {
				try {
					// one transaction per batch: a failed batch is reported and the rest still go in
					List<Object[]> rows = toRows(batch, auditor, LocalDateTime.now());
					transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT_SQL, rows));
				} catch (DataAccessException ex) {
					failed += batch.size();
					String error = "batch starting at " + batch.get(0).getAssetUrl() + ": "
							+ ex.getMostSpecificCause().getMessage();
					log.error("Import {}", error, ex);
					if (errors.size() < MAX_SAMPLES) {
						errors.add(error);
					}
					continue;
				}
			}
			imported += batch.size();
			for (Product product : batch) {
				byBrand.merge(product.getSource() + "/" + product.getSupplier() + "/" + product.getBrand(), 1L, Long::sum);
				byAssetType.merge(String.valueOf(product.getAssetType()), 1L, Long::sum);
			}
			log.info("Import progress: {}/{}", imported + failed, pending.size());
		}

		long md5Filled = 0;
		for (int from = 0; from < md5Updates.size(); from += BATCH_SIZE) {
			List<Object[]> batch = md5Updates.subList(from, Math.min(from + BATCH_SIZE, md5Updates.size()));
			if (!dryRun) {
				try {
					transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(MD5_UPDATE_SQL, batch));
				} catch (DataAccessException ex) {
					String error = "MD5 batch starting at " + batch.get(0)[1] + ": " + ex.getMostSpecificCause().getMessage();
					log.error("Import {}", error, ex);
					if (errors.size() < MAX_SAMPLES) {
						errors.add(error);
					}
					continue;
				}
			}
			md5Filled += batch.size();
		}

		ImportReport report = new ImportReport(bucket, prefix, dryRun, listed, folders, systemFiles, invalid, already,
				imported, failed, md5Filled, byBrand, byAssetType, skippedSamples, errors,
				System.currentTimeMillis() - start);
		log.info("Import gs://{}/{} finished: listed={}, imported={}, already={}, md5Filled={}, failed={}, skipped={} in {} ms",
				bucket, prefix, listed, imported, already, md5Filled, failed, folders + systemFiles + invalid,
				report.durationMs());
		return report;
	}

	static Product toProduct(GcsAssetPath path, String url, String contentMd5) {
		Product product = new Product();
		product.setUuid(UUID.randomUUID().toString());
		product.setSource(path.source());
		product.setSupplier(path.supplier());
		product.setBrand(path.brand());
		product.setAssetType(path.assetType());
		product.setAssetName(path.fileName());
		// also the lookup key's name; AEM may later overwrite it with the same original name
		product.setOrigAssetName(path.fileName());
		product.setTitle(path.fileNameWithoutExtension());
		product.setAssetUrl(url);
		product.setActive(Boolean.TRUE);
		product.setContentMd5(contentMd5);
		// JDBC inserts skip the entity callbacks, so the lookup hash is set here
		product.setLookupKeyHash(LookupKey.hashOf(product.getSource(), product.getSupplier(), product.getBrand(),
				product.getAssetName()));
		return product;
	}

	static List<Object[]> toRows(List<Product> batch, String auditor, LocalDateTime now) {
		Timestamp timestamp = Timestamp.valueOf(now);
		List<Object[]> rows = new ArrayList<>(batch.size());
		for (Product p : batch) {
			rows.add(new Object[] { p.getUuid(), p.getTitle(), p.getSource(), p.getSupplier(), p.getBrand(),
					p.getAssetType(), p.getAssetName(), p.getOrigAssetName(), p.getAssetUrl(), p.getActive(),
					timestamp, auditor, timestamp, auditor, p.getLookupKeyHash(), p.getContentMd5() });
		}
		return rows;
	}

	private static void addSample(List<String> samples, String sample) {
		if (samples.size() < MAX_SAMPLES) {
			samples.add(sample);
		}
	}

}
