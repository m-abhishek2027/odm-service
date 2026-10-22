package com.odm.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odm.dto.ImportReport;
import com.odm.importer.GcsAssetImportService;

import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/v1/admin/import", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AssetImportController {

	private final GcsAssetImportService gcsAssetImportService;

	@Value("${odm.import.gcs.bucket}")
	private String defaultBucket;

	@Value("${odm.import.gcs.prefix}")
	private String defaultPrefix;

	// dryRun=true lists and maps everything but writes nothing; re-running skips objects already imported
	@PostMapping("/gcs")
	public ResponseEntity<ImportReport> importFromGcs(
			@RequestParam(required = false)
			@Pattern(regexp = "[a-z0-9][a-z0-9._-]{1,220}[a-z0-9]", message = "invalid bucket name") String bucket,
			@RequestParam(required = false)
			@Pattern(regexp = "([^/].*/)?", message = "prefix must be empty or end with '/' and not start with '/'") String prefix,
			@RequestParam(defaultValue = "false") boolean dryRun){
		return ResponseEntity.ok(gcsAssetImportService.importAssets(
				bucket != null ? bucket : defaultBucket,
				prefix != null ? prefix : defaultPrefix,
				dryRun));
	}

}
