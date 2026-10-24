package com.odm.exception;

import java.util.Map;

// The lookup request as a whole is unusable (for example a duplicate referenceId); answered with 400
public class InvalidLookupRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final Map<String, String> errors;

	public InvalidLookupRequestException(Map<String, String> errors) {
		super("Invalid lookup request: " + errors);
		this.errors = Map.copyOf(errors);
	}

	public Map<String, String> getErrors() {
		return errors;
	}

}
