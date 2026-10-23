package com.odm.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.odm.lookup.LookupKeyBackfill.Row;

@ExtendWith(MockitoExtension.class)
class LookupKeyBackfillTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	@InjectMocks
	private LookupKeyBackfill backfill;

	@SuppressWarnings("unchecked")
	private void pages(String after1, List<Row> page1, String after2, List<Row> page2) {
		when(jdbcTemplate.query(eq(LookupKeyBackfill.SELECT_SQL), any(RowMapper.class), eq(after1),
				eq(LookupKeyBackfill.BATCH_SIZE))).thenReturn((List) page1);
		if (after2 != null) {
			when(jdbcTemplate.query(eq(LookupKeyBackfill.SELECT_SQL), any(RowMapper.class), eq(after2),
					eq(LookupKeyBackfill.BATCH_SIZE))).thenReturn((List) page2);
		}
	}

	private static List<Row> fullPage(String prefix) {
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < LookupKeyBackfill.BATCH_SIZE; i++) {
			rows.add(new Row(prefix + String.format("%04d", i), "external", "autocomp", "bosch", "f" + i + ".jpg"));
		}
		return rows;
	}

	@Test
	@SuppressWarnings("unchecked")
	void fillsHashesPageByPageWithKeysetPaging() {
		List<Row> first = fullPage("a");
		List<Row> second = List.of(new Row("b0001", "External", "AutoComp", "Bosch", "X.jpg"));
		pages("", first, "a0999", second);

		int updated = backfill.backfill();

		assertThat(updated).isEqualTo(LookupKeyBackfill.BATCH_SIZE + 1);
		ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
		verify(jdbcTemplate, org.mockito.Mockito.times(2)).batchUpdate(eq(LookupKeyBackfill.UPDATE_SQL), captor.capture());
		Object[] last = captor.getAllValues().get(1).get(0);
		assertThat(last[0]).isEqualTo(LookupKey.hashOf("external", "autocomp", "bosch", "x.jpg"));
		assertThat(last[1]).isEqualTo("b0001");
	}

	@Test
	@SuppressWarnings("unchecked")
	void rowsWithIncompleteKeysKeepANullHash() {
		pages("", List.of(new Row("u1", null, "autocomp", "bosch", "x.jpg"), new Row("u2", "e", "s", "b", "y.jpg")),
				null, null);

		assertThat(backfill.backfill()).isEqualTo(1);
		ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
		verify(jdbcTemplate).batchUpdate(eq(LookupKeyBackfill.UPDATE_SQL), captor.capture());
		assertThat(captor.getValue()).extracting(r -> r[1]).containsExactly("u2");
	}

	@Test
	void nothingToDoWritesNothing() {
		pages("", List.of(), null, null);

		assertThat(backfill.backfill()).isZero();
		verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
	}

	@Test
	@SuppressWarnings("unchecked")
	void startupNeverFailsWhenDatabaseIsDown() {
		when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
				.thenThrow(new DataAccessResourceFailureException("down"));

		assertThatCode(backfill::backfillOnStartup).doesNotThrowAnyException();
	}

}
