package com.odm.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.google.cloud.storage.StorageException;
import com.odm.dto.ImportReport;
import com.odm.exception.ImportInProgressException;
import com.odm.importer.GcsAssetImportService;

@WebMvcTest(AssetImportController.class)
@TestPropertySource(properties = { "odm.import.gcs.bucket=default-bucket", "odm.import.gcs.prefix=products/" })
class AssetImportControllerTest {

	private static final String URL = "/api/v1/admin/import/gcs";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GcsAssetImportService gcsAssetImportService;

	private static ImportReport report(String bucket, String prefix, boolean dryRun) {
		return new ImportReport(bucket, prefix, dryRun, 3, 1, 0, 0, 0, 2, 0, Map.of("external/autocomp/bosch", 2L),
				Map.of("stills", 2L), List.of(), List.of(), 10);
	}

	@Test
	void usesConfiguredBucketAndPrefixByDefault() throws Exception {
		when(gcsAssetImportService.importAssets("default-bucket", "products/", false))
				.thenReturn(report("default-bucket", "products/", false));

		mockMvc.perform(post(URL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imported").value(2))
				.andExpect(jsonPath("$.byBrand['external/autocomp/bosch']").value(2));
	}

	@Test
	void acceptsBucketPrefixAndDryRun() throws Exception {
		when(gcsAssetImportService.importAssets("other-bucket", "products/2026/", true))
				.thenReturn(report("other-bucket", "products/2026/", true));

		mockMvc.perform(post(URL).param("bucket", "other-bucket").param("prefix", "products/2026/").param("dryRun", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dryRun").value(true));
	}

	@Test
	void rejectsPrefixWithoutTrailingSlash() throws Exception {
		mockMvc.perform(post(URL).param("prefix", "products"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.prefix").exists());

		verify(gcsAssetImportService, never()).importAssets(anyString(), anyString(), anyBoolean());
	}

	@Test
	void rejectsInvalidBucketName() throws Exception {
		mockMvc.perform(post(URL).param("bucket", "Bad Bucket!"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.bucket").exists());
	}

	@Test
	void concurrentImportReturns409() throws Exception {
		when(gcsAssetImportService.importAssets(anyString(), anyString(), anyBoolean()))
				.thenThrow(new ImportInProgressException("An import is already running; try again when it has finished"));

		mockMvc.perform(post(URL)).andExpect(status().isConflict());
	}

	@Test
	void storageAccessDeniedReturns502() throws Exception {
		when(gcsAssetImportService.importAssets(anyString(), anyString(), anyBoolean()))
				.thenThrow(new StorageException(403, "denied"));

		mockMvc.perform(post(URL))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.detail").value("Access to the bucket was denied; check the service credentials"));
	}

	@Test
	void missingBucketReturns502() throws Exception {
		when(gcsAssetImportService.importAssets(anyString(), anyString(), anyBoolean()))
				.thenThrow(new StorageException(404, "not found"));

		mockMvc.perform(post(URL))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.detail").value("Bucket not found"));
	}

}
