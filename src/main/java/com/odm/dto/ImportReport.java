package com.odm.dto;

import java.util.List;
import java.util.Map;

/**
 * Outcome of a bucket import. objectsListed = foldersSkipped + systemFilesSkipped + invalidPathsSkipped
 * + alreadyImported + imported + failed (in a dry run, imported counts what would be inserted).
 */
public record ImportReport(
		String bucket,
		String prefix,
		boolean dryRun,
		long objectsListed,
		long foldersSkipped,
		long systemFilesSkipped,
		long invalidPathsSkipped,
		long alreadyImported,
		long imported,
		long failed,
		// "source/supplier/brand" -> assets imported (or to import)
		Map<String, Long> byBrand,
		Map<String, Long> byAssetType,
		// objects skipped as system files or invalid paths, first few only
		List<String> skippedSamples,
		List<String> errors,
		long durationMs) {
}
