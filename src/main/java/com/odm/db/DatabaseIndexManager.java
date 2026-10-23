package com.odm.db;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.odm.dto.IndexStatus;
import com.odm.dto.IndexStatus.State;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the secondary indexes of the products table. ddl-auto=update creates tables and columns but
 * cannot express MySQL prefix indexes, so the indexes are created here: on every startup any missing
 * index is added, and existing ones are left alone. Safe to run on several instances at once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseIndexManager {

	record IndexDefinition(String name, String table, String columns, boolean unique) {

		IndexDefinition(String name, String table, String columns) {
			this(name, table, columns, false);
		}
	}

	// Four full VARCHAR(255) utf8mb4 columns exceed InnoDB's 3072-byte key limit, hence the prefixes;
	// equality lookups still use the index.
	static final List<IndexDefinition> INDEXES = List.of(
			// POST /digitalAsset/lookup: lookup_key_hash IN (...)
			new IndexDefinition("idx_products_lookup_key_hash", "products", "lookup_key_hash"),
			// GET /digitalAsset source/supplier/brand/origAssetName filters
			new IndexDefinition("idx_products_lookup", "products",
					"source(100), supplier(100), brand(100), orig_asset_name(191), active"),
			// GET /digitalAsset with ?active=..., sorted by createdOn
			new IndexDefinition("idx_products_active_created_on", "products", "active, created_on"),
			new IndexDefinition("idx_products_created_on", "products", "created_on"),
			new IndexDefinition("idx_products_modified_on", "products", "modified_on"),
			new IndexDefinition("idx_products_created_by", "products", "created_by"),
			new IndexDefinition("idx_products_modified_by", "products", "modified_by"),
			new IndexDefinition("idx_products_asset_sub_category", "products", "asset_sub_category"),
			// bucket import: skips objects whose gs:// url is already stored; unique so that imports running on
			// several instances at once can never store the same object twice (NULL urls are allowed many times)
			new IndexDefinition("uk_products_asset_url", "products", "asset_url(500)", true));

	// replaced indexes, dropped once their replacement exists: index name -> replacement
	static final Map<String, String> OBSOLETE_INDEXES = Map.of("idx_products_asset_url", "uk_products_asset_url");

	private static final String EXISTS_SQL = "SELECT COUNT(*) FROM information_schema.statistics "
			+ "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?";

	private final JdbcTemplate jdbcTemplate;

	@Value("${odm.db.indexes.auto-create:true}")
	private boolean autoCreate;

	@EventListener(ApplicationReadyEvent.class)
	public void createMissingIndexesOnStartup() {
		if (!autoCreate) {
			log.info("Index auto-creation disabled (odm.db.indexes.auto-create=false)");
			return;
		}
		try {
			List<IndexStatus> result = ensureIndexes();
			long failed = result.stream().filter(s -> s.state() == State.FAILED).count();
			if (failed > 0) {
				log.error("{} of {} database indexes could not be created; see earlier errors", failed, result.size());
			}
		} catch (RuntimeException ex) {
			// a missing index only slows queries down, so it must never stop the application
			log.error("Database index check failed", ex);
		}
	}

	public List<IndexStatus> status() {
		return INDEXES.stream()
				.map(def -> toStatus(def, exists(def) ? State.EXISTS : State.MISSING, null))
				.toList();
	}

	public List<IndexStatus> ensureIndexes() {
		List<IndexStatus> result = INDEXES.stream().map(this::ensure).toList();
		dropObsoleteIndexes(result);
		return result;
	}

	private void dropObsoleteIndexes(List<IndexStatus> current) {
		OBSOLETE_INDEXES.forEach((obsolete, replacement) -> {
			boolean replacementReady = current.stream()
					.anyMatch(s -> s.name().equals(replacement) && s.state() != State.FAILED && s.state() != State.MISSING);
			IndexDefinition old = new IndexDefinition(obsolete, "products", "");
			if (!replacementReady || !exists(old)) {
				return;
			}
			try {
				jdbcTemplate.execute("DROP INDEX " + obsolete + " ON products ALGORITHM=INPLACE LOCK=NONE");
				log.info("Dropped index {} (replaced by {})", obsolete, replacement);
			} catch (DataAccessException ex) {
				log.warn("Could not drop obsolete index {}: {}", obsolete, ex.getMostSpecificCause().getMessage());
			}
		});
	}

	private IndexStatus ensure(IndexDefinition def) {
		if (exists(def)) {
			return toStatus(def, State.EXISTS, null);
		}
		// ALGORITHM=INPLACE, LOCK=NONE: build online without blocking reads/writes, or fail instead of locking
		String ddl = "CREATE " + (def.unique() ? "UNIQUE " : "") + "INDEX " + def.name() + " ON " + def.table() + " (" + def.columns() + ")"
				+ " ALGORITHM=INPLACE LOCK=NONE";
		try {
			long start = System.currentTimeMillis();
			jdbcTemplate.execute(ddl);
			log.info("Created index {} on {} ({}) in {} ms", def.name(), def.table(), def.columns(),
					System.currentTimeMillis() - start);
			return toStatus(def, State.CREATED, null);
		} catch (DataAccessException ex) {
			// another instance may have created it at the same time
			if (exists(def)) {
				return toStatus(def, State.EXISTS, null);
			}
			String reason = ex.getMostSpecificCause().getMessage();
			log.error("Could not create index {}: {}", def.name(), reason);
			return toStatus(def, State.FAILED, reason);
		}
	}

	private boolean exists(IndexDefinition def) {
		Integer count = jdbcTemplate.queryForObject(EXISTS_SQL, Integer.class, def.table(), def.name());
		return count != null && count > 0;
	}

	private static IndexStatus toStatus(IndexDefinition def, State state, String message) {
		return new IndexStatus(def.name(), def.table(), def.columns(), state, message);
	}

}
