package com.odm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.odm.dto.MetadataUpdate;
import com.odm.dto.MetadataUpdateResponse;
import com.odm.dto.MetadataUpdateResult;
import com.odm.dto.MetadataUpdateStatus;
import com.odm.entity.ColorSpace;
import com.odm.exception.InvalidUpdateRequestException;
import com.odm.metadata.MetadataField;

import jakarta.validation.Validation;

class AssetMetadataUpdateServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
	private static final Instant T10 = Instant.parse("2026-10-25T10:00:00Z");
	private static final Instant T11 = Instant.parse("2026-10-25T11:00:00Z");

	// position of the trailing parameters in UPDATE_SQL, after the 25 field values
	private static final int P_MODIFIED_AT = 25, P_CHANGED = 26, P_UUID = 30, P_GUARD = 31;

	private NamedParameterJdbcTemplate namedJdbc;
	private JdbcTemplate jdbc;
	private AssetMetadataUpdateServiceImpl service;

	/** stored database rows by uuid: column -> value */
	private final Map<String, Map<String, Object>> db = new HashMap<>();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		namedJdbc = mock(NamedParameterJdbcTemplate.class);
		jdbc = mock(JdbcTemplate.class);
		AuditorAware<String> auditor = () -> Optional.of("aem-sync");
		service = new AssetMetadataUpdateServiceImpl(namedJdbc, jdbc,
				Validation.buildDefaultValidatorFactory().getValidator(), auditor);
		service.clock = Clock.fixed(NOW, ZoneOffset.UTC);

		doAnswer(inv -> {
			List<String> uuids = (List<String>) ((Map<String, Object>) inv.getArgument(1)).get("uuids");
			RowCallbackHandler handler = inv.getArgument(2);
			for (String uuid : uuids) {
				Map<String, Object> row = db.get(uuid);
				if (row != null) {
					handler.processRow(resultSet(uuid, row));
				}
			}
			return null;
		}).when(namedJdbc).query(anyString(), anyMap(), any(RowCallbackHandler.class));

		when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(inv -> {
			int[] counts = new int[((List<?>) inv.getArgument(1)).size()];
			java.util.Arrays.fill(counts, 1);
			return counts;
		});
	}

	private static ResultSet resultSet(String uuid, Map<String, Object> row) throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString(anyString())).thenAnswer(inv -> {
			String column = inv.getArgument(0);
			if (column.equals("uuid")) {
				return uuid;
			}
			Object value = row.get(column);
			return value == null ? null : value.toString();
		});
		when(rs.getObject(anyString(), any(Class.class))).thenAnswer(inv -> row.get((String) inv.getArgument(0)));
		return rs;
	}

	private void stored(String uuid, boolean active, Instant metadataModifiedAt, Object... columnValues) {
		Map<String, Object> row = new HashMap<>();
		row.put("active", active);
		row.put("metadata_modified_at", metadataModifiedAt == null ? null : LocalDateTime.ofInstant(metadataModifiedAt, ZoneOffset.UTC));
		for (int i = 0; i < columnValues.length; i += 2) {
			row.put((String) columnValues[i], columnValues[i + 1]);
		}
		db.put(uuid, row);
	}

	private static MetadataUpdate item(String uuid, Instant modifiedAt) {
		MetadataUpdate u = new MetadataUpdate();
		u.setUuid(uuid);
		u.setMetadataModifiedAt(modifiedAt);
		return u;
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> writtenRows() {
		ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
		verify(jdbc).batchUpdate(eq(AssetMetadataUpdateServiceImpl.UPDATE_SQL), captor.capture());
		return captor.getValue();
	}

	private static int col(MetadataField field) {
		return field.ordinal();
	}

	@Test
	void sqlCoversExactlyTheMetadataFieldsAndGuardsOnTimestamp() {
		assertThat(MetadataField.values()).hasSize(25);
		assertThat(AssetMetadataUpdateServiceImpl.UPDATE_SQL)
				.contains("dpi = COALESCE(?, dpi)", "color_space = COALESCE(?, color_space)")
				.contains("WHERE uuid = ? AND active = TRUE AND (metadata_modified_at IS NULL OR metadata_modified_at < ?)")
				// key fields, lookup hash and active are never written (", source =" would be a key column; file_source is metadata)
				.doesNotContain(", source =", "supplier =", "brand =", " asset_name =", "title", "lookup_key_hash",
						"active = COALESCE");
		assertThat(AssetMetadataUpdateServiceImpl.UPDATE_SQL.chars().filter(c -> c == '?').count()).isEqualTo(32);
		assertThat(AssetMetadataUpdateServiceImpl.SELECT_SQL).endsWith("FOR UPDATE");
	}

	@Test
	void newerMetadataIsAppliedAndReportsChangedFields() {
		stored("u1", true, T10, "dpi", 72.0, "color_space", "RGB", "ok_thumb", true);
		MetadataUpdate u = item("u1", T11);
		u.setDpi(300.0);
		u.setColorSpace(ColorSpace.RGB);
		u.setOkThumb(false);

		MetadataUpdateResponse response = service.update("r", List.of(u));

		MetadataUpdateResult result = response.results().get(0);
		assertThat(result.status()).isEqualTo(MetadataUpdateStatus.UPDATED);
		assertThat(result.changedFields()).containsExactly("dpi", "okThumb");
		Object[] row = writtenRows().get(0);
		assertThat(row[col(MetadataField.DPI)]).isEqualTo(300.0);
		assertThat(row[col(MetadataField.COLOR_SPACE)]).isEqualTo("RGB");
		assertThat(row[col(MetadataField.OK_THUMB)]).isEqualTo(false);
		// fields not sent are passed as null so COALESCE keeps them
		assertThat(row[col(MetadataField.ORIG_FILE_TYPE)]).isNull();
		assertThat(row[P_MODIFIED_AT]).isEqualTo(LocalDateTime.of(2026, 10, 25, 11, 0));
		assertThat(row[P_CHANGED]).isEqualTo(true);
		assertThat(row[P_UUID]).isEqualTo("u1");
		assertThat(row[P_GUARD]).isEqualTo(LocalDateTime.of(2026, 10, 25, 11, 0));
		assertThat(response.status()).isEqualTo(MetadataUpdateResponse.SUCCESS);
	}

	@Test
	void firstMetadataForAnAssetIsApplied() {
		stored("u1", true, null);
		MetadataUpdate u = item("u1", T10);
		u.setFileSource("External");

		assertThat(service.update("r", List.of(u)).results().get(0).status()).isEqualTo(MetadataUpdateStatus.UPDATED);
	}

	@Test
	void newerTimestampWithSameValuesIsUnchangedButStillAdvancesTheTimestamp() {
		stored("u1", true, T10, "dpi", 300.0);
		MetadataUpdate u = item("u1", T11);
		u.setDpi(300.0);

		MetadataUpdateResult result = service.update("r", List.of(u)).results().get(0);

		assertThat(result.status()).isEqualTo(MetadataUpdateStatus.UNCHANGED);
		Object[] row = writtenRows().get(0);
		assertThat(row[P_MODIFIED_AT]).isEqualTo(LocalDateTime.of(2026, 10, 25, 11, 0));
		// modified_on / modified_by stay as they are
		assertThat(row[P_CHANGED]).isEqualTo(false);
	}

	@Test
	void sameRequestAgainIsUnchangedWithoutAnyWrite() {
		stored("u1", true, T10, "dpi", 300.0);
		MetadataUpdate u = item("u1", T10);
		u.setDpi(300.0);

		assertThat(service.update("r", List.of(u)).results().get(0).status()).isEqualTo(MetadataUpdateStatus.UNCHANGED);
		verify(jdbc, never()).batchUpdate(anyString(), anyList());
	}

	@Test
	void sameTimestampWithDifferentValuesIsStale() {
		stored("u1", true, T10, "dpi", 300.0);
		MetadataUpdate u = item("u1", T10);
		u.setDpi(72.0);

		MetadataUpdateResult result = service.update("r", List.of(u)).results().get(0);

		assertThat(result.status()).isEqualTo(MetadataUpdateStatus.STALE);
		assertThat(result.storedMetadataModifiedAt()).isEqualTo(T10);
		verify(jdbc, never()).batchUpdate(anyString(), anyList());
	}

	@Test
	void olderMetadataIsStaleAndNeverWritten() {
		stored("u1", true, T11, "dpi", 600.0);
		MetadataUpdate u = item("u1", T10);
		u.setDpi(300.0);

		MetadataUpdateResult result = service.update("r", List.of(u)).results().get(0);

		assertThat(result.status()).isEqualTo(MetadataUpdateStatus.STALE);
		assertThat(result.storedMetadataModifiedAt()).isEqualTo(T11);
		verify(jdbc, never()).batchUpdate(anyString(), anyList());
	}

	@Test
	void unknownAndDeletedAssets() {
		stored("gone", false, T10);

		MetadataUpdateResponse response = service.update("r", List.of(item("missing", T11), item("gone", T11)));

		assertThat(response.results()).extracting(MetadataUpdateResult::status)
				.containsExactly(MetadataUpdateStatus.NOT_FOUND, MetadataUpdateStatus.DELETED);
		assertThat(response.status()).isEqualTo(MetadataUpdateResponse.NONE_APPLIED);
		verify(jdbc, never()).batchUpdate(anyString(), anyList());
	}

	@Test
	void blankAndMissingFieldsKeepTheStoredValue() {
		stored("u1", true, T10, "orig_file_type", "TIFF", "dpi", 300.0);
		MetadataUpdate u = item("u1", T11);
		u.setOrigFileType("   ");
		u.setAdjFileType("JPEG");

		MetadataUpdateResult result = service.update("r", List.of(u)).results().get(0);

		assertThat(result.changedFields()).containsExactly("adjFileType");
		Object[] row = writtenRows().get(0);
		assertThat(row[col(MetadataField.ORIG_FILE_TYPE)]).isNull();
		assertThat(row[col(MetadataField.DPI)]).isNull();
		assertThat(row[col(MetadataField.ADJ_FILE_TYPE)]).isEqualTo("JPEG");
	}

	@Test
	void falseAndZeroAreRealValues() {
		stored("u1", true, T10, "ok_thumb", true, "dpi", 300.0);
		MetadataUpdate u = item("u1", T11);
		u.setOkThumb(false);
		u.setDpi(0.0);

		assertThat(service.update("r", List.of(u)).results().get(0).changedFields()).containsExactly("dpi", "okThumb");
	}

	@Test
	void subMicrosecondTimesCompareEqualToWhatTheDatabaseKeeps() {
		stored("u1", true, T10, "date_processed", LocalDateTime.of(2026, 10, 25, 9, 0, 0, 123_456_000));
		MetadataUpdate u = item("u1", T10.plusNanos(789));
		u.setDateProcessed(LocalDateTime.of(2026, 10, 25, 9, 0, 0, 123_456_789));

		// timestamp and date both equal after truncation to microseconds: the same update again
		assertThat(service.update("r", List.of(u)).results().get(0).status()).isEqualTo(MetadataUpdateStatus.UNCHANGED);
		verify(jdbc, never()).batchUpdate(anyString(), anyList());
	}

	@Test
	void invalidItemsAreReportedAndTheRestOfTheBatchIsApplied() {
		stored("ok", true, T10);
		MetadataUpdate good = item("ok", T11);
		good.setDpi(300.0);
		MetadataUpdate negative = item("neg", T11);
		negative.setDpi(-1.0);
		MetadataUpdate noTimestamp = item("nots", null);
		MetadataUpdate future = item("future", NOW.plusSeconds(3600));
		MetadataUpdate unknown = item("unknown", T11);
		unknown.addUnknownField("title", "x");
		unknown.addUnknownField("source", "y");
		MetadataUpdate tooLong = item("long", T11);
		tooLong.setFileSource("x".repeat(256));

		MetadataUpdateResponse response = service.update("r", List.of(good, negative, noTimestamp, future, unknown, tooLong));

		List<MetadataUpdateResult> r = response.results();
		assertThat(r).extracting(MetadataUpdateResult::status).containsExactly(MetadataUpdateStatus.UPDATED,
				MetadataUpdateStatus.INVALID, MetadataUpdateStatus.INVALID, MetadataUpdateStatus.INVALID,
				MetadataUpdateStatus.INVALID, MetadataUpdateStatus.INVALID);
		assertThat(r.get(1).errors()).containsKey("dpi");
		assertThat(r.get(2).errors()).containsEntry("metadataModifiedAt", "metadataModifiedAt is required");
		assertThat(r.get(3).errors()).containsEntry("metadataModifiedAt", "metadataModifiedAt is in the future");
		assertThat(r.get(4).errors()).containsEntry("title", "not an updatable metadata field")
				.containsEntry("source", "not an updatable metadata field");
		assertThat(r.get(5).errors()).containsKey("fileSource");
		assertThat(response.summary()).isEqualTo(new MetadataUpdateResponse.Summary(6, 1, 0, 0, 0, 0, 5));
		assertThat(response.status()).isEqualTo(MetadataUpdateResponse.PARTIAL);
		assertThat(writtenRows()).hasSize(1);
	}

	@Test
	void slightClockSkewIsAccepted() {
		stored("u1", true, T10);

		assertThat(service.update("r", List.of(item("u1", NOW.plusSeconds(60)))).results().get(0).status())
				.isEqualTo(MetadataUpdateStatus.UNCHANGED);
	}

	@Test
	void missingOrDuplicateUuidRejectsTheRequest() {
		assertThatThrownBy(() -> service.update("r", List.of(item("a", T10), item(" ", T10), item("a", T10))))
				.isInstanceOf(InvalidUpdateRequestException.class)
				.satisfies(ex -> assertThat(((InvalidUpdateRequestException) ex).getErrors())
						.containsEntry("assets[1].uuid", "uuid is required")
						.containsEntry("assets[2].uuid", "duplicate uuid, also at assets[0]"));
		verify(namedJdbc, never()).query(anyString(), anyMap(), any(RowCallbackHandler.class));
	}

	@Test
	void wholeBatchIsReadOnceAndWrittenOnce() {
		List<MetadataUpdate> items = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			stored("u" + i, true, T10);
			MetadataUpdate u = item("u" + i, T11);
			u.setDpi((double) i);
			items.add(u);
		}

		MetadataUpdateResponse response = service.update("r", items);

		verify(namedJdbc, times(1)).query(anyString(), anyMap(), any(RowCallbackHandler.class));
		verify(jdbc, times(1)).batchUpdate(anyString(), anyList());
		assertThat(response.summary().updated()).isEqualTo(200);
	}

	@Test
	void writeRefusedByTheDatabaseGuardIsReportedFromTheCurrentRow() {
		stored("u1", true, T10);
		when(jdbc.batchUpdate(anyString(), anyList())).thenAnswer(inv -> {
			// another writer stored newer metadata between the read and the write
			stored("u1", true, Instant.parse("2026-10-25T11:30:00Z"));
			return new int[] { 0 };
		});
		MetadataUpdate u = item("u1", T11);
		u.setDpi(300.0);

		MetadataUpdateResult result = service.update("r", List.of(u)).results().get(0);

		assertThat(result.status()).isEqualTo(MetadataUpdateStatus.STALE);
		assertThat(result.storedMetadataModifiedAt()).isEqualTo(Instant.parse("2026-10-25T11:30:00Z"));
	}

	@Test
	void auditorIsWrittenOnlyWhenSomethingChanged() {
		stored("u1", true, T10);
		MetadataUpdate u = item("u1", T11);
		u.setDpi(300.0);

		service.update("r", List.of(u));

		Object[] row = writtenRows().get(0);
		assertThat(row[28]).isEqualTo(true);
		assertThat(row[29]).isEqualTo("aem-sync");
	}

}
