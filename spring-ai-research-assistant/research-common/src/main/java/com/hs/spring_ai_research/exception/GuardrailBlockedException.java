package com.hs.spring_ai_research.exception;

/**
 * Thrown when a request is blocked by the guardrail chain
 * (prompt injection, PII detected, content moderation failure).
 * Caught by {@link GlobalExceptionHandler} and returned as a 400 response.
 */
public class GuardrailBlockedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public GuardrailBlockedException(String message) {
		super(message);
	}
}
