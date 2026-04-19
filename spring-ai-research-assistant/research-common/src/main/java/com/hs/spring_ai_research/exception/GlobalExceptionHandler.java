package com.hs.spring_ai_research.exception;

import java.time.Instant;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.hs.spring_ai_research.dto.ErrorResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Catches exceptions from all controllers and returns a standardized {@link ErrorResponse}.
 *
 * <p>Handled exceptions:</p>
 * <ul>
 *   <li>Validation errors → 400 with field-level messages</li>
 *   <li>File too large → 413</li>
 *   <li>Guardrail blocked → 400 with the block reason</li>
 *   <li>Unhandled exceptions → 500 (details logged, not exposed to client)</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(
			MethodArgumentNotValidException ex, WebRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Validation failed");

		return ResponseEntity.badRequest().body(new ErrorResponse(
				400, "Validation Error", message,
				request.getDescription(false), Instant.now()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSize(
			MaxUploadSizeExceededException ex, WebRequest request) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorResponse(
				413, "File Too Large", "File size exceeds the maximum allowed limit of 20MB.",
				request.getDescription(false), Instant.now()));
	}

	@ExceptionHandler(GuardrailBlockedException.class)
	public ResponseEntity<ErrorResponse> handleGuardrailBlocked(
			GuardrailBlockedException ex, WebRequest request) {
		return ResponseEntity.badRequest().body(new ErrorResponse(
				400, "Request Blocked", ex.getMessage(),
				request.getDescription(false), Instant.now()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneral(
			Exception ex, WebRequest request) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
				500, "Internal Server Error",
				"An unexpected error occurred. Please try again later.",
				request.getDescription(false), Instant.now()));
	}
}
