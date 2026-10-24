package com.odm.lookup;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fills products.lookup_key_hash for rows written before the column existed (or by any writer that skipped it).
 * Runs at every startup; after the first run it finds nothing to do. Rows whose key is incomplete keep a null
 * hash and are simply not findable by lookup. Safe on several instances at once: they compute the same values.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LookupKeyBackfill {

	static final int BATCH_SIZE = 1000;

	static final String SELECT_SQL = "SELECT uuid, source, supplier, brand, asset_name FROM products "
			+ "WHERE lookup_key_hash IS NULL AND uuid > ? ORDER BY uuid LIMIT ?";

	static final String UPDATE_SQL = "UPDATE products SET lookup_key_hash = ? WHERE uuid = ? AND lookup_key_hash IS NULL";

	private final JdbcTemplate jdbcTemplate;

	record Row(String uuid, String source, String supplier, String brand, String assetName) {
	}

	@EventListener(ApplicationReadyEvent.class)
	public void backfillOnStartup() {
		try {
			long start = System.currentTimeMillis();
			int updated = backfill();
			if (updated > 0) {
				log.info("Lookup key hash backfilled for {} assets in {} ms", updated, System.currentTimeMillis() - start);
			}
		} catch (RuntimeException ex) {
			// assets without a hash are only missing from lookup until the next start
			log.error("Lookup key hash backfill failed", ex);
		}
	}

	/** @return rows updated */
	public int backfill() {
		int updated = 0;
		String after = "";
		while (true) {
			// keyset paging on uuid: rows left null (incomplete key) are never read twice
			List<Row> rows = jdbcTemplate.query(SELECT_SQL, (rs, i) -> new Row(rs.getString(1), rs.getString(2),
					rs.getString(3), rs.getString(4), rs.getString(5)), after, BATCH_SIZE);
			if (rows.isEmpty()) {
				return updated;
			}
			List<Object[]> updates = new ArrayList<>(rows.size());
			for (Row row : rows) {
				String hash = LookupKey.hashOf(row.source(), row.supplier(), row.brand(), row.assetName());
				if (hash != null) {
					updates.add(new Object[] { hash, row.uuid() });
				}
			}
			if (!updates.isEmpty()) {
				jdbcTemplate.batchUpdate(UPDATE_SQL, updates);
				updated += updates.size();
			}
			if (rows.size() < BATCH_SIZE) {
				return updated;
			}
			after = rows.get(rows.size() - 1).uuid();
		}
	}

}
