package com.hs.spring_ai_research.dto;

import java.time.Instant;

/** Standardized error response returned by the {@link com.hs.spring_ai_research.exception.GlobalExceptionHandler}. */
public record ErrorResponse(
		int status,
		String error,
		String message,
		String path,
		Instant timestamp
) {}
