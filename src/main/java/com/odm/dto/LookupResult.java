package com.odm.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result for one requested item, in request order. asset is set only for FOUND, errors only for INVALID;
 * matchCount counts the active assets with the item's key (FOUND = 1, DUPLICATE = 2 or more).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LookupResult(String referenceId, LookupStatus status, boolean retryable, Integer matchCount,
		AssetMetadataItem asset, Map<String, String> errors) {

	// retryable always follows the status, so the two can never disagree
	private static LookupResult of(String referenceId, LookupStatus status, Integer matchCount, AssetMetadataItem asset,
			Map<String, String> errors) {
		return new LookupResult(referenceId, status, status.retryable(), matchCount, asset, errors);
	}

	public static LookupResult found(String referenceId, AssetMetadataItem asset) {
		return of(referenceId, LookupStatus.FOUND, 1, asset, null);
	}

	public static LookupResult notFound(String referenceId) {
		return of(referenceId, LookupStatus.NOT_FOUND, null, null, null);
	}

	public static LookupResult duplicate(String referenceId, int matchCount) {
		return of(referenceId, LookupStatus.DUPLICATE, matchCount, null, null);
	}

	public static LookupResult deleted(String referenceId) {
		return of(referenceId, LookupStatus.DELETED, null, null, null);
	}

	public static LookupResult invalid(String referenceId, Map<String, String> errors) {
		return of(referenceId, LookupStatus.INVALID, null, null, errors);
	}

}
