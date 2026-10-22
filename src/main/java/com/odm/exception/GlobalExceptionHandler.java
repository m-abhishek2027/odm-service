package com.odm.exception;

import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.google.cloud.storage.StorageException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return validationProblem(errors);
	}

	// raised for constraints on the request body list itself or its elements (POST array)
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ProblemDetail handleMethodValidation(HandlerMethodValidationException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			String prefix = result.getContainerIndex() != null ? "[" + result.getContainerIndex() + "]" : "";
			if (result instanceof ParameterErrors parameterErrors) {
				for (FieldError error : parameterErrors.getFieldErrors()) {
					errors.putIfAbsent(prefix + "." + error.getField(), error.getDefaultMessage());
				}
			} else {
				String name = prefix.isEmpty() ? result.getMethodParameter().getParameterName() : prefix;
				for (MessageSourceResolvable error : result.getResolvableErrors()) {
					errors.putIfAbsent(name, error.getDefaultMessage());
				}
			}
		}
		return validationProblem(errors);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
		// unknown enum value, e.g. colorSpace "sRGB": report the field and the allowed values
		if (ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType() != null
				&& ife.getTargetType().isEnum()) {
			String allowed = Arrays.stream(ife.getTargetType().getEnumConstants())
					.map(Object::toString)
					.collect(Collectors.joining(", "));
			return validationProblem(Map.of(jsonPath(ife),
					"invalid value '" + ife.getValue() + "', allowed values: " + allowed));
		}
		// valid JSON of the wrong shape, e.g. an object where an array is expected
		if (ex.getCause() instanceof MismatchedInputException mie && mie.getTargetType() != null) {
			String path = jsonPath(mie);
			return validationProblem(Map.of(path.isEmpty() ? "body" : path,
					"invalid type, expected " + describe(mie.getTargetType())));
		}
		if (ex.getCause() == null) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing");
		}
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request body");
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ImportInProgressException.class)
	public ProblemDetail handleImportInProgress(ImportInProgressException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	// Cloud Storage refused or failed the call (missing bucket, no permission, outage)
	@ExceptionHandler(StorageException.class)
	public ProblemDetail handleStorage(StorageException ex) {
		log.error("Cloud Storage error (code {})", ex.getCode(), ex);
		String detail = switch (ex.getCode()) {
			case 401, 403 -> "Access to the bucket was denied; check the service credentials";
			case 404 -> "Bucket not found";
			default -> "Cloud Storage is unavailable, please retry";
		};
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, detail);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Request conflicts with existing data");
	}

	// database unreachable, connection pool exhausted or query timed out: the client may retry
	@ExceptionHandler({ DataAccessResourceFailureException.class, TransientDataAccessException.class,
			CannotCreateTransactionException.class })
	public ProblemDetail handleDatabaseUnavailable(Exception ex) {
		log.error("Database unavailable", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Database is temporarily unavailable, please retry");
	}

	@ExceptionHandler(DataAccessException.class)
	public ProblemDetail handleDataAccess(DataAccessException ex) {
		log.error("Database error", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Database error");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		// keep Spring's own status for framework errors such as 404 route, 405 or 415
		if (ex instanceof ErrorResponse errorResponse) {
			return errorResponse.getBody();
		}
		log.error("Unhandled error", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
	}

	// same key style as validation errors: "colorSpace" or "[1].colorSpace"
	private static String jsonPath(JacksonException ex) {
		StringBuilder path = new StringBuilder();
		for (JacksonException.Reference ref : ex.getPath()) {
			if (ref.getPropertyName() != null) {
				if (!path.isEmpty()) {
					path.append('.');
				}
				path.append(ref.getPropertyName());
			} else if (ref.getIndex() >= 0) {
				path.append('[').append(ref.getIndex()).append(']');
			}
		}
		return path.toString();
	}

	private static String describe(Class<?> type) {
		if (Collection.class.isAssignableFrom(type) || type.isArray()) {
			return "array";
		}
		if (CharSequence.class.isAssignableFrom(type) || type.isEnum()) {
			return "string";
		}
		if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class)) {
			return "number";
		}
		if (type == Boolean.class || type == boolean.class) {
			return "boolean";
		}
		if (Temporal.class.isAssignableFrom(type)) {
			return "date-time string";
		}
		return "object";
	}

	private ProblemDetail validationProblem(Map<String, String> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", errors);
		return problem;
	}

}
