package com.odm.exception;

import java.util.Map;

// The metadata update request as a whole is unusable (missing or duplicate uuid); answered with 400
public class InvalidUpdateRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final Map<String, String> errors;

	public InvalidUpdateRequestException(Map<String, String> errors) {
		super("Invalid metadata update request: " + errors);
		this.errors = Map.copyOf(errors);
	}

	public Map<String, String> getErrors() {
		return errors;
	}

}
