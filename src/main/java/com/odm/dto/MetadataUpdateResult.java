package com.odm.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result for one update item, in request order. changedFields is set only for UPDATED, storedMetadataModifiedAt
 * for STALE (the newer timestamp ODM already holds), errors only for INVALID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetadataUpdateResult(String uuid, MetadataUpdateStatus status, List<String> changedFields,
		Instant storedMetadataModifiedAt, Map<String, String> errors) {

	public static MetadataUpdateResult updated(String uuid, List<String> changedFields) {
		return new MetadataUpdateResult(uuid, MetadataUpdateStatus.UPDATED, changedFields, null, null);
	}

	public static MetadataUpdateResult unchanged(String uuid) {
		return new MetadataUpdateResult(uuid, MetadataUpdateStatus.UNCHANGED, null, null, null);
	}

	public static MetadataUpdateResult notFound(String uuid) {
		return new MetadataUpdateResult(uuid, MetadataUpdateStatus.NOT_FOUND, null, null, null);
	}

	public static MetadataUpdateResult deleted(String uuid) {
		return new MetadataUpdateResult(uuid, MetadataUpdateStatus.DELETED, null, null, null);
	}

	public static MetadataUpdateResult stale(String uuid, Instant storedMetadataModifiedAt) {
		return new MetadataUpdateResult(uuid, MetadataUpdateStatus.STALE, null, storedMetadataModifiedAt, null);
	}

	public static MetadataUpdateResult invalid(String uuid, Map<String, String> errors) {
		return new MetadataUpdateResult(uuid, MetadataUpdateStatus.INVALID, null, null, errors);
	}

}
