package com.odm.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response of POST /digitalAsset/metadata/batch: one result per requested item, in request order.
 * status is SUCCESS when every item is UPDATED or UNCHANGED, NONE_APPLIED when none is, otherwise PARTIAL.
 */
public record MetadataUpdateResponse(String requestId, Instant timestamp, String status, Summary summary,
		List<MetadataUpdateResult> results) {

	public static final String SUCCESS = "SUCCESS";
	public static final String PARTIAL = "PARTIAL";
	public static final String NONE_APPLIED = "NONE_APPLIED";

	// updated + unchanged + notFound + deleted + stale + invalid = requested
	public record Summary(int requested, int updated, int unchanged, int notFound, int deleted, int stale,
			int invalid) {
	}

}
