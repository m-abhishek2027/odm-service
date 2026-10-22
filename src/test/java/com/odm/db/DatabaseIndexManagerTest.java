package com.odm.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.odm.dto.IndexStatus;
import com.odm.dto.IndexStatus.State;

@ExtendWith(MockitoExtension.class)
class DatabaseIndexManagerTest {

	private static final String LOOKUP = "idx_products_lookup";

	@Mock
	private JdbcTemplate jdbcTemplate;

	@InjectMocks
	private DatabaseIndexManager manager;

	private void indexExists(String name, Integer... counts) {
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("products"), eq(name)))
				.thenReturn(counts[0], java.util.Arrays.copyOfRange(counts, 1, counts.length));
	}

	private void allOthersExist() {
		DatabaseIndexManager.INDEXES.stream()
				.filter(def -> !def.name().equals(LOOKUP))
				.forEach(def -> indexExists(def.name(), 1));
	}

	private static IndexStatus lookupStatus(List<IndexStatus> result) {
		return result.stream().filter(s -> s.name().equals(LOOKUP)).findFirst().orElseThrow();
	}

	@Test
	void existingIndexesAreLeftAlone() {
		DatabaseIndexManager.INDEXES.forEach(def -> indexExists(def.name(), 1));

		List<IndexStatus> result = manager.ensureIndexes();

		assertThat(result).hasSize(DatabaseIndexManager.INDEXES.size()).allMatch(s -> s.state() == State.EXISTS);
		verify(jdbcTemplate, never()).execute(anyString());
	}

	@Test
	void missingIndexIsCreatedOnlineWithPrefixColumns() {
		allOthersExist();
		indexExists(LOOKUP, 0);

		List<IndexStatus> result = manager.ensureIndexes();

		assertThat(lookupStatus(result).state()).isEqualTo(State.CREATED);
		verify(jdbcTemplate).execute("CREATE INDEX idx_products_lookup ON products "
				+ "(source(100), supplier(100), brand(100), orig_asset_name(191), active) ALGORITHM=INPLACE LOCK=NONE");
	}

	@Test
	void assetUrlIndexIsCreatedUnique() {
		DatabaseIndexManager.INDEXES.stream()
				.filter(def -> !def.name().equals("uk_products_asset_url"))
				.forEach(def -> indexExists(def.name(), 1));
		indexExists("uk_products_asset_url", 0);

		manager.ensureIndexes();

		verify(jdbcTemplate).execute(
				"CREATE UNIQUE INDEX uk_products_asset_url ON products (asset_url(500)) ALGORITHM=INPLACE LOCK=NONE");
	}

	@Test
	void obsoleteIndexIsDroppedOnceReplacementExists() {
		DatabaseIndexManager.INDEXES.forEach(def -> indexExists(def.name(), 1));
		indexExists("idx_products_asset_url", 1);

		manager.ensureIndexes();

		verify(jdbcTemplate).execute("DROP INDEX idx_products_asset_url ON products ALGORITHM=INPLACE LOCK=NONE");
	}

	@Test
	void obsoleteIndexIsKeptWhenReplacementFailed() {
		DatabaseIndexManager.INDEXES.stream()
				.filter(def -> !def.name().equals("uk_products_asset_url"))
				.forEach(def -> indexExists(def.name(), 1));
		indexExists("uk_products_asset_url", 0, 0);
		doThrow(new BadSqlGrammarException("create", "CREATE UNIQUE INDEX",
				new java.sql.SQLException("Duplicate entry")))
				.when(jdbcTemplate).execute(anyString());

		manager.ensureIndexes();

		verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.startsWith("DROP INDEX"));
	}

	@Test
	void indexCreatedConcurrentlyByAnotherInstanceCountsAsExisting() {
		allOthersExist();
		indexExists(LOOKUP, 0, 1);
		doThrow(new BadSqlGrammarException("create", "CREATE INDEX", new java.sql.SQLException("Duplicate key name")))
				.when(jdbcTemplate).execute(anyString());

		assertThat(lookupStatus(manager.ensureIndexes()).state()).isEqualTo(State.EXISTS);
	}

	@Test
	void failedCreationIsReportedNotThrown() {
		allOthersExist();
		indexExists(LOOKUP, 0, 0);
		doThrow(new BadSqlGrammarException("create", "CREATE INDEX", new java.sql.SQLException("Unknown column 'brand'")))
				.when(jdbcTemplate).execute(anyString());

		IndexStatus status = lookupStatus(manager.ensureIndexes());

		assertThat(status.state()).isEqualTo(State.FAILED);
		assertThat(status.message()).contains("Unknown column 'brand'");
	}

	@Test
	void statusNeverCreatesAnything() {
		allOthersExist();
		indexExists(LOOKUP, 0);

		assertThat(lookupStatus(manager.status()).state()).isEqualTo(State.MISSING);
		verify(jdbcTemplate, never()).execute(anyString());
	}

	@Test
	void startupNeverFailsEvenWhenDatabaseIsDown() {
		ReflectionTestUtils.setField(manager, "autoCreate", true);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("products"), anyString()))
				.thenThrow(new DataAccessResourceFailureException("down"));

		assertThatCode(manager::createMissingIndexesOnStartup).doesNotThrowAnyException();
	}

	@Test
	void startupDoesNothingWhenDisabled() {
		ReflectionTestUtils.setField(manager, "autoCreate", false);

		manager.createMissingIndexesOnStartup();

		verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class), eq("products"), anyString());
	}

}
