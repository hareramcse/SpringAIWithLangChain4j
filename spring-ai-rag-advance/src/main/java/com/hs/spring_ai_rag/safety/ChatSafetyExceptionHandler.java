package com.hs.spring_ai_rag.safety;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ChatSafetyExceptionHandler {

	@ExceptionHandler(UnsafeQueryException.class)
	public ResponseEntity<Map<String, String>> badQuery(UnsafeQueryException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(UnsafeResponseException.class)
	public ResponseEntity<Map<String, String>> unsafeResponse(UnsafeResponseException e) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("error", e.getMessage()));
	}
}
