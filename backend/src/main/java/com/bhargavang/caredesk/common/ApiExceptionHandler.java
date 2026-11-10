package com.bhargavang.caredesk.common;

import java.util.LinkedHashMap;
import java.util.Map;

import com.bhargavang.caredesk.booking.SlotUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 problem responses. Never includes stack traces
 * or SQL in the response body.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
	}

	@ExceptionHandler(BusinessRuleException.class)
	ProblemDetail rule(BusinessRuleException e) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Can't do that", e.getMessage());
	}

	@ExceptionHandler(SlotUnavailableException.class)
	ProblemDetail slotTaken(SlotUnavailableException e) {
		return problem(HttpStatus.CONFLICT, "Slot taken", e.getMessage());
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	ProblemDetail busy(PessimisticLockingFailureException e) {
		log.warn("Lock wait timed out while booking: {}", e.getMessage());
		return problem(HttpStatus.CONFLICT, "Busy", "That slot is being booked right now. Please try again.");
	}

	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail badLogin(BadCredentialsException e) {
		return problem(HttpStatus.UNAUTHORIZED, "Login failed", "Email or password is incorrect.");
	}

	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<ProblemDetail> tooMany(TooManyRequestsException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()))
				.body(problem(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts", e.getMessage()));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Some fields need fixing.");
		body.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(body);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setTitle(title);
		return pd;
	}
}
