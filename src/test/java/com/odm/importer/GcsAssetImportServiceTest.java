package com.odm.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.odm.dto.ImportReport;
import com.odm.exception.ImportInProgressException;
import com.odm.repo.ProductRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GcsAssetImportServiceTest {

	private static final String BUCKET = "oreilly-dam-staging";
	private static final String PREFIX = "products/";
	private static final String BASE = "products/2026/03/16/external/autocomp/bosch/stills/15u/i5u/";

	// column positions in GcsAssetImportService.INSERT_SQL
	private static final int UUID_COL = 0, TITLE = 1, SOURCE = 2, SUPPLIER = 3, BRAND = 4, ASSET_TYPE = 5,
			ASSET_NAME = 6, ORIG_ASSET_NAME = 7, ASSET_URL = 8, ACTIVE = 9, CREATED_ON = 10, CREATED_BY = 11,
			MODIFIED_ON = 12, MODIFIED_BY = 13;

	@Mock
	private Storage storage;

	@Mock
	private ProductRepository productRepo;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private AuditorAware<String> auditorAware;

	private GcsAssetImportService service;

	@BeforeEach
	void setUp() {
		service = new GcsAssetImportService(storage, productRepo, jdbcTemplate, transactionTemplate, auditorAware);
		when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("gcs-import"));
		doAnswer(inv -> {
			inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
	}

	private static Blob blob(String name, String contentType) {
		Blob blob = mock(Blob.class);
		when(blob.getName()).thenReturn(name);
		when(blob.getContentType()).thenReturn(contentType);
		return blob;
	}

	@SuppressWarnings("unchecked")
	private void bucketContains(Blob... blobs) {
		Page<Blob> page = mock(Page.class);
		when(page.iterateAll()).thenReturn(List.of(blobs));
		when(storage.list(eq(BUCKET), any(Storage.BlobListOption[].class))).thenReturn(page);
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> insertedRows() {
		ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
		verify(jdbcTemplate, atLeastOnce()).batchUpdate(eq(GcsAssetImportService.INSERT_SQL), captor.capture());
		List<Object[]> all = new ArrayList<>();
		captor.getAllValues().forEach(all::addAll);
		return all;
	}

	@Test
	void insertSqlOnlyWritesImportFieldsAndLeavesAemFieldsAlone() {
		assertThat(GcsAssetImportService.INSERT_SQL)
				.contains("source", "supplier", "brand", "asset_type", "asset_name", "orig_asset_name", "asset_url")
				.doesNotContain("file_source", "orig_file_type", "color_space", "file_size_in_kb", "dpi", "ok_");
		long placeholders = GcsAssetImportService.INSERT_SQL.chars().filter(c -> c == '?').count();
		assertThat(placeholders).isEqualTo(14);
	}

	@Test
	void importsEveryAssetWithFieldsFromItsPath() {
		bucketContains(
				blob("products/2026/03/16/external/autocomp/", "text/plain"),
				blob(BASE + "batch7/13cb25186_01_009.jpg", "image/jpeg"),
				blob("products/2026/03/16/external/delphiauto/delphi/0bc/k4y/0bck4ymbo.jpg", "image/jpeg"));

		ImportReport report = service.importAssets(BUCKET, PREFIX, false);

		List<Object[]> rows = insertedRows();
		assertThat(rows).hasSize(2);
		Object[] bosch = rows.get(0);
		assertThat((String) bosch[UUID_COL]).hasSize(36);
		assertThat(bosch[SOURCE]).isEqualTo("external");
		assertThat(bosch[SUPPLIER]).isEqualTo("autocomp");
		assertThat(bosch[BRAND]).isEqualTo("bosch");
		assertThat(bosch[ASSET_TYPE]).isEqualTo("stills");
		assertThat(bosch[ASSET_NAME]).isEqualTo("13cb25186_01_009.jpg");
		assertThat(bosch[ORIG_ASSET_NAME]).isEqualTo("13cb25186_01_009.jpg");
		assertThat(bosch[TITLE]).isEqualTo("13cb25186_01_009");
		assertThat(bosch[ASSET_URL]).isEqualTo("gs://" + BUCKET + "/" + BASE + "batch7/13cb25186_01_009.jpg");
		assertThat(bosch[ACTIVE]).isEqualTo(true);
		assertThat(bosch[CREATED_BY]).isEqualTo("gcs-import");
		assertThat(bosch[MODIFIED_BY]).isEqualTo("gcs-import");
		assertThat(bosch[CREATED_ON]).isInstanceOf(Timestamp.class).isEqualTo(bosch[MODIFIED_ON]);

		assertThat(rows.get(1)[BRAND]).isEqualTo("delphi");
		assertThat(rows.get(1)[ASSET_TYPE]).isEqualTo("stills");
		assertThat(rows.get(0)[UUID_COL]).isNotEqualTo(rows.get(1)[UUID_COL]);

		assertThat(report.objectsListed()).isEqualTo(3);
		assertThat(report.foldersSkipped()).isEqualTo(1);
		assertThat(report.imported()).isEqualTo(2);
		assertThat(report.byBrand()).containsEntry("external/autocomp/bosch", 1L)
				.containsEntry("external/delphiauto/delphi", 1L);
		assertThat(report.byAssetType()).containsEntry("stills", 2L);
	}

	@Test
	void sameFileNameInDifferentFoldersBecomesSeparateAssets() {
		bucketContains(blob(BASE + "batch4/11cb25125_02_002.jpg", "image/jpeg"),
				blob(BASE + "batch7/11cb25125_02_002.jpg", "image/jpeg"));

		service.importAssets(BUCKET, PREFIX, false);

		assertThat(insertedRows()).extracting(r -> r[ASSET_URL]).doesNotHaveDuplicates().hasSize(2);
	}

	@Test
	void skipsSystemFilesAndUnexpectedPaths() {
		bucketContains(blob(BASE + "Images/Thumbs.db", "application/octet-stream"),
				blob("products/misc/readme.txt", "text/plain"),
				blob(BASE + "Images/360s.zip", "application/zip"));

		ImportReport report = service.importAssets(BUCKET, PREFIX, false);

		assertThat(report.systemFilesSkipped()).isEqualTo(1);
		assertThat(report.invalidPathsSkipped()).isEqualTo(1);
		assertThat(report.imported()).isEqualTo(1);
		assertThat(report.skippedSamples()).anyMatch(s -> s.contains("Thumbs.db")).anyMatch(s -> s.contains("readme.txt"));
		assertThat(insertedRows()).extracting(r -> r[ASSET_NAME]).containsExactly("360s.zip");
	}

	@Test
	void reRunSkipsAlreadyImportedObjects() {
		String existing = "gs://" + BUCKET + "/" + BASE + "a.jpg";
		when(productRepo.findAssetUrlsLike("gs://" + BUCKET + "/" + PREFIX + "%")).thenReturn(List.of(existing));
		bucketContains(blob(BASE + "a.jpg", "image/jpeg"), blob(BASE + "b.jpg", "image/jpeg"));

		ImportReport report = service.importAssets(BUCKET, PREFIX, false);

		assertThat(report.alreadyImported()).isEqualTo(1);
		assertThat(report.imported()).isEqualTo(1);
		assertThat(insertedRows()).extracting(r -> r[ASSET_NAME]).containsExactly("b.jpg");
	}

	@Test
	void dryRunWritesNothing() {
		bucketContains(blob(BASE + "a.jpg", "image/jpeg"));

		ImportReport report = service.importAssets(BUCKET, PREFIX, true);

		assertThat(report.dryRun()).isTrue();
		assertThat(report.imported()).isEqualTo(1);
		verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
		verify(transactionTemplate, never()).executeWithoutResult(any());
	}

	@Test
	void writesInBatches() {
		int total = GcsAssetImportService.BATCH_SIZE * 2 + 1;
		Blob[] blobs = new Blob[total];
		for (int i = 0; i < total; i++) {
			blobs[i] = blob(BASE + "f" + i + ".jpg", "image/jpeg");
		}
		bucketContains(blobs);

		ImportReport report = service.importAssets(BUCKET, PREFIX, false);

		verify(transactionTemplate, times(3)).executeWithoutResult(any());
		assertThat(insertedRows()).hasSize(total);
		assertThat(report.imported()).isEqualTo(total);
	}

	@Test
	void failedBatchIsReportedAndOthersContinue() {
		int total = GcsAssetImportService.BATCH_SIZE + 1;
		Blob[] blobs = new Blob[total];
		for (int i = 0; i < total; i++) {
			blobs[i] = blob(BASE + "f" + i + ".jpg", "image/jpeg");
		}
		bucketContains(blobs);
		doAnswer(inv -> {
			throw new DataAccessResourceFailureException("connection lost");
		}).doAnswer(inv -> {
			inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());

		ImportReport report = service.importAssets(BUCKET, PREFIX, false);

		assertThat(report.failed()).isEqualTo(GcsAssetImportService.BATCH_SIZE);
		assertThat(report.imported()).isEqualTo(1);
		assertThat(report.errors()).singleElement().asString().contains("connection lost");
	}

	@Test
	void secondConcurrentImportIsRejected() throws Exception {
		CountDownLatch listing = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(storage.list(eq(BUCKET), any(Storage.BlobListOption[].class))).thenAnswer(inv -> {
			listing.countDown();
			release.await();
			@SuppressWarnings("unchecked")
			Page<Blob> page = mock(Page.class);
			when(page.iterateAll()).thenReturn(List.of());
			return page;
		});
		AtomicReference<Throwable> first = new AtomicReference<>();
		Thread thread = new Thread(() -> {
			try {
				service.importAssets(BUCKET, PREFIX, false);
			} catch (Throwable t) {
				first.set(t);
			}
		});
		thread.start();
		listing.await();

		assertThatThrownBy(() -> service.importAssets(BUCKET, PREFIX, false))
				.isInstanceOf(ImportInProgressException.class);

		release.countDown();
		thread.join();
		assertThat(first.get()).isNull();
		// lock released after the first import finished
		bucketContains();
		assertThat(service.importAssets(BUCKET, PREFIX, true).objectsListed()).isZero();
	}

}
