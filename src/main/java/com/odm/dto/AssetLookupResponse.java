package com.odm.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response of POST /digitalAsset/lookup: one result per requested item, in request order.
 * status is SUCCESS when every item is FOUND, NONE_FOUND when none is, otherwise PARTIAL.
 */
public record AssetLookupResponse(String requestId, Instant timestamp, String status, Summary summary,
		List<LookupResult> results) {

	public static final String SUCCESS = "SUCCESS";
	public static final String PARTIAL = "PARTIAL";
	public static final String NONE_FOUND = "NONE_FOUND";

	// found + notFound + duplicate + deleted + invalid = requested
	public record Summary(int requested, int found, int notFound, int duplicate, int deleted, int invalid) {
	}

}
