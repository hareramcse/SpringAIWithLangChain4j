package com.hs.spring_ai_help_desk.dto;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

	private int status;
	private String error;
	private String message;
	private Instant timestamp;
	private Map<String, String> fieldErrors;

	public static ErrorResponse of(int status, String error, String message) {
		return ErrorResponse.builder()
				.status(status)
				.error(error)
				.message(message)
				.timestamp(Instant.now())
				.build();
	}

	public static ErrorResponse withFieldErrors(int status, String error, String message, Map<String, String> fieldErrors) {
		return ErrorResponse.builder()
				.status(status)
				.error(error)
				.message(message)
				.timestamp(Instant.now())
				.fieldErrors(fieldErrors)
				.build();
	}

}
