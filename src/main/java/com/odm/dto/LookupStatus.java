package com.odm.dto;

// Outcome of one lookup item. retryable marks the outcomes that can change without any change on the AEM side:
// NOT_FOUND (the asset may be imported later) and DUPLICATE (the duplicate copies may be removed). AEM decides when.
public enum LookupStatus {

	// exactly one active asset has this key
	FOUND(false),
	// no asset, active or deleted, has this key
	NOT_FOUND(true),
	// two or more active assets have this key: no data is returned until the duplicates are removed
	DUPLICATE(true),
	// only soft-deleted assets have this key
	DELETED(false),
	// the item failed validation
	INVALID(false);

	private final boolean retryable;

	LookupStatus(boolean retryable) {
		this.retryable = retryable;
	}

	public boolean retryable() {
		return retryable;
	}

}
