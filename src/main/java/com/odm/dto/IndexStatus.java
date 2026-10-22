package com.odm.dto;

public record IndexStatus(String name, String table, String columns, State state, String message) {

	public enum State {
		// already present before this run
		EXISTS,
		// created by this run
		CREATED,
		// not present (status check only, nothing was attempted)
		MISSING,
		// creation attempted and failed; see message and logs
		FAILED
	}

}
