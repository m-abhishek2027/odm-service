package com.odm.dto;

// Outcome of one metadata update item. None of them changes by sending the same item again, so none is retryable;
// a failed batch as a whole (503) is the only thing to retry.
public enum MetadataUpdateStatus {

	// newer metadata; at least one stored value changed
	UPDATED,
	// newer or same metadata whose values are already stored; nothing but the metadata timestamp is written
	UNCHANGED,
	// no asset has this uuid
	NOT_FOUND,
	// the asset is soft-deleted
	DELETED,
	// older than the stored metadata, or the same timestamp with different values: nothing is written
	STALE,
	// the item failed validation
	INVALID

}
