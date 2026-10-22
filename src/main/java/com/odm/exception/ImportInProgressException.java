package com.odm.exception;

public class ImportInProgressException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ImportInProgressException(String message) {
		super(message);
	}

}
