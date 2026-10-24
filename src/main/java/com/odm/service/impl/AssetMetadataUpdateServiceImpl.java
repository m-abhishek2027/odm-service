package com.odm.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.odm.dto.MetadataItemChecks;
import com.odm.dto.MetadataUpdate;
import com.odm.dto.MetadataUpdateResponse;
import com.odm.dto.MetadataUpdateResult;
import com.odm.dto.MetadataUpdateStatus;
import com.odm.exception.InvalidUpdateRequestException;
import com.odm.metadata.MetadataField;
import com.odm.service.AssetMetadataUpdateService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies AEM-generated metadata (the 25 {@link MetadataField}s) to assets by uuid, one result per item.
 *
 * Accuracy rules:
 * - only the 25 metadata fields can change; a missing, null or blank field keeps its stored value;
 * - an item is applied only when its metadataModifiedAt is newer than the stored one (the same timestamp with the
 *   same values is UNCHANGED, with different values STALE), so a delayed or repeated request never overwrites
 *   newer metadata;
 * - the rows are locked (SELECT ... FOR UPDATE) for the whole transaction and every UPDATE repeats the timestamp
 *   check, so concurrent requests for the same asset are applied in timestamp order;
 * - modified_on / modified_by change only when a value changes.
 *
 * Performance: one SELECT and one JDBC batch UPDATE per request, whatever its size.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMetadataUpdateServiceImpl implements AssetMetadataUpdateService {

	// a metadataModifiedAt further ahead than this is rejected: it would block every later update
	static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

	private static final List<MetadataField> FIELDS = List.of(MetadataField.values());

	static final String SELECT_SQL = "SELECT uuid, active, metadata_modified_at, "
			+ FIELDS.stream().map(MetadataField::column).collect(Collectors.joining(", "))
			+ " FROM products WHERE uuid IN (:uuids) FOR UPDATE";

	// COALESCE(?, column): a null parameter keeps the stored value
	static final String UPDATE_SQL = "UPDATE products SET "
			+ FIELDS.stream().map(f -> f.column() + " = COALESCE(?, " + f.column() + ")").collect(Collectors.joining(", "))
			+ ", metadata_modified_at = ?, modified_on = IF(?, ?, modified_on), modified_by = IF(?, ?, modified_by)"
			+ " WHERE uuid = ? AND active = TRUE AND (metadata_modified_at IS NULL OR metadata_modified_at < ?)";

	private final NamedParameterJdbcTemplate namedJdbc;

	private final JdbcTemplate jdbc;

	private final Validator validator;

	private final AuditorAware<String> auditorAware;

	// replaceable in tests
	Clock clock = Clock.systemUTC();

	record StoredRow(boolean active, Instant metadataModifiedAt, Map<MetadataField, Object> values) {
	}

	private record Write(int index, String uuid, Instant metadataModifiedAt, Map<MetadataField, Object> requested,
			List<String> changed) {
	}

	@Override
	@Transactional(timeoutString = "${odm.update.timeout-seconds:20}")
	public MetadataUpdateResponse update(String requestId, List<MetadataUpdate> items) {
		long start = System.currentTimeMillis();
		rejectMissingOrDuplicateUuids(items);

		MetadataUpdateResult[] results = new MetadataUpdateResult[items.size()];
		List<Integer> valid = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			Map<String, String> errors = validate(items.get(i));
			if (errors.isEmpty()) {
				valid.add(i);
			} else {
				results[i] = MetadataUpdateResult.invalid(items.get(i).getUuid(), errors);
			}
		}

		Map<String, StoredRow> stored = valid.isEmpty() ? Map.of()
				: lockAndRead(valid.stream().map(i -> items.get(i).getUuid()).toList());

		List<Write> writes = new ArrayList<>();
		for (int i : valid) {
			MetadataUpdate item = items.get(i);
			String uuid = item.getUuid();
			StoredRow row = stored.get(uuid);
			Instant incoming = item.getMetadataModifiedAt().truncatedTo(ChronoUnit.MICROS);
			Map<MetadataField, Object> requested = requestedValues(item);
			if (row == null) {
				results[i] = MetadataUpdateResult.notFound(uuid);
			} else if (!row.active()) {
				results[i] = MetadataUpdateResult.deleted(uuid);
			} else if (row.metadataModifiedAt() == null || incoming.isAfter(row.metadataModifiedAt())) {
				writes.add(new Write(i, uuid, incoming, requested, changedFields(requested, row.values())));
			} else if (incoming.equals(row.metadataModifiedAt()) && changedFields(requested, row.values()).isEmpty()) {
				// the same update again: nothing to write
				results[i] = MetadataUpdateResult.unchanged(uuid);
			} else {
				results[i] = MetadataUpdateResult.stale(uuid, row.metadataModifiedAt());
			}
		}

		applyWrites(writes, results);

		List<MetadataUpdateResult> resultList = Arrays.asList(results);
		MetadataUpdateResponse.Summary summary = summarize(resultList);
		int applied = summary.updated() + summary.unchanged();
		String status = applied == summary.requested() ? MetadataUpdateResponse.SUCCESS
				: applied == 0 ? MetadataUpdateResponse.NONE_APPLIED : MetadataUpdateResponse.PARTIAL;
		log.info("metadata update requestId={} requested={} updated={} unchanged={} notFound={} deleted={} stale={} "
				+ "invalid={} durationMs={}", requestId, summary.requested(), summary.updated(), summary.unchanged(),
				summary.notFound(), summary.deleted(), summary.stale(), summary.invalid(),
				System.currentTimeMillis() - start);
		return new MetadataUpdateResponse(requestId, Instant.now(clock), status, summary, resultList);
	}

	private static void rejectMissingOrDuplicateUuids(List<MetadataUpdate> items) {
		Map<String, Integer> firstIndex = new HashMap<>();
		Map<String, String> errors = new LinkedHashMap<>();
		for (int i = 0; i < items.size(); i++) {
			String uuid = items.get(i).getUuid();
			if (uuid == null || uuid.isBlank()) {
				errors.put("assets[" + i + "].uuid", "uuid is required");
				continue;
			}
			Integer first = firstIndex.putIfAbsent(uuid, i);
			if (first != null) {
				errors.put("assets[" + i + "].uuid", "duplicate uuid, also at assets[" + first + "]");
			}
		}
		if (!errors.isEmpty()) {
			throw new InvalidUpdateRequestException(errors);
		}
	}

	private Map<String, String> validate(MetadataUpdate item) {
		Map<String, String> errors = new TreeMap<>();
		for (ConstraintViolation<MetadataUpdate> violation : validator.validate(item, MetadataItemChecks.class)) {
			errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
		}
		for (String name : item.getUnknownFields().keySet()) {
			errors.put(name, "not an updatable metadata field");
		}
		Instant modifiedAt = item.getMetadataModifiedAt();
		if (modifiedAt != null && modifiedAt.isAfter(Instant.now(clock).plus(MAX_CLOCK_SKEW))) {
			errors.put("metadataModifiedAt", "metadataModifiedAt is in the future");
		}
		return errors;
	}

	// locks the rows until the transaction ends, so a concurrent update of the same asset waits for this one
	private Map<String, StoredRow> lockAndRead(List<String> uuids) {
		Map<String, StoredRow> rows = new HashMap<>();
		namedJdbc.query(SELECT_SQL, Map.of("uuids", uuids), rs -> {
			Map<MetadataField, Object> values = new EnumMap<>(MetadataField.class);
			for (MetadataField field : FIELDS) {
				values.put(field, field.read(rs));
			}
			LocalDateTime modifiedAt = rs.getObject("metadata_modified_at", LocalDateTime.class);
			rows.put(rs.getString("uuid"), new StoredRow(Boolean.TRUE.equals(rs.getObject("active", Boolean.class)),
					modifiedAt == null ? null : modifiedAt.toInstant(ZoneOffset.UTC), values));
		});
		return rows;
	}

	private static Map<MetadataField, Object> requestedValues(MetadataUpdate item) {
		Map<MetadataField, Object> requested = new EnumMap<>(MetadataField.class);
		for (MetadataField field : FIELDS) {
			Object value = field.requested(item);
			if (value != null) {
				requested.put(field, value);
			}
		}
		return requested;
	}

	private static List<String> changedFields(Map<MetadataField, Object> requested, Map<MetadataField, Object> stored) {
		List<String> changed = new ArrayList<>();
		requested.forEach((field, value) -> {
			if (!Objects.equals(value, stored.get(field))) {
				changed.add(field.jsonName());
			}
		});
		return changed;
	}

	private void applyWrites(List<Write> writes, MetadataUpdateResult[] results) {
		if (writes.isEmpty()) {
			return;
		}
		String auditor = auditorAware.getCurrentAuditor().orElse("system");
		LocalDateTime now = LocalDateTime.now();
		List<Object[]> params = new ArrayList<>(writes.size());
		for (Write w : writes) {
			Object[] row = new Object[FIELDS.size() + 7];
			int p = 0;
			for (MetadataField field : FIELDS) {
				row[p++] = MetadataField.toJdbc(w.requested().get(field));
			}
			LocalDateTime modifiedAt = LocalDateTime.ofInstant(w.metadataModifiedAt(), ZoneOffset.UTC);
			boolean changed = !w.changed().isEmpty();
			row[p++] = modifiedAt;
			row[p++] = changed;
			row[p++] = now;
			row[p++] = changed;
			row[p++] = auditor;
			row[p++] = w.uuid();
			row[p] = modifiedAt;
			params.add(row);
		}
		int[] counts = jdbc.batchUpdate(UPDATE_SQL, params);
		for (int k = 0; k < writes.size(); k++) {
			Write w = writes.get(k);
			// 0 rows: the guard in the WHERE clause refused it, which the row lock should make impossible
			if (k < counts.length && counts[k] == 0) {
				log.warn("metadata update for {} was not applied by the database guard", w.uuid());
				results[w.index()] = recheck(w.uuid());
			} else {
				results[w.index()] = w.changed().isEmpty() ? MetadataUpdateResult.unchanged(w.uuid())
						: MetadataUpdateResult.updated(w.uuid(), List.copyOf(w.changed()));
			}
		}
	}

	private MetadataUpdateResult recheck(String uuid) {
		StoredRow row = lockAndRead(List.of(uuid)).get(uuid);
		if (row == null) {
			return MetadataUpdateResult.notFound(uuid);
		}
		return row.active() ? MetadataUpdateResult.stale(uuid, row.metadataModifiedAt())
				: MetadataUpdateResult.deleted(uuid);
	}

	private static MetadataUpdateResponse.Summary summarize(List<MetadataUpdateResult> results) {
		Map<MetadataUpdateStatus, Integer> counts = new EnumMap<>(MetadataUpdateStatus.class);
		results.forEach(r -> counts.merge(r.status(), 1, Integer::sum));
		return new MetadataUpdateResponse.Summary(results.size(), counts.getOrDefault(MetadataUpdateStatus.UPDATED, 0),
				counts.getOrDefault(MetadataUpdateStatus.UNCHANGED, 0), counts.getOrDefault(MetadataUpdateStatus.NOT_FOUND, 0),
				counts.getOrDefault(MetadataUpdateStatus.DELETED, 0), counts.getOrDefault(MetadataUpdateStatus.STALE, 0),
				counts.getOrDefault(MetadataUpdateStatus.INVALID, 0));
	}

}
