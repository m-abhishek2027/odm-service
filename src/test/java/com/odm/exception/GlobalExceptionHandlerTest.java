package com.odm.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void clientDisconnectWhileWritingIsNotAnError() {
		HttpMessageNotWritableException ex = new HttpMessageNotWritableException("Could not write JSON",
				new AsyncRequestNotUsableException("ServletOutputStream failed to write", new IOException("reset")));

		assertThat(handler.handleNotWritable(ex)).isNull();
	}

	@Test
	void otherWriteFailureIs500() {
		ResponseEntity<ProblemDetail> response = handler.handleNotWritable(
				new HttpMessageNotWritableException("Could not write JSON", new IllegalStateException("bad getter")));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void transientDetection() {
		assertThat(GlobalExceptionHandler.isTransient(new RuntimeException(new SQLTimeoutException("x")))).isTrue();
		assertThat(GlobalExceptionHandler.isTransient(
				new org.hibernate.TransactionException("Transaction timeout expired"))).isTrue();
		assertThat(GlobalExceptionHandler.isTransient(new org.hibernate.TransactionException("Unable to commit"))).isFalse();
		assertThat(GlobalExceptionHandler.isTransient(new IllegalStateException("x"))).isFalse();
	}

}
