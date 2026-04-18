package com.hs.spring_ai_adviser.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hs.spring_ai_adviser.adviser.ChatAdvisor.BlockedContentException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BlockedContentException.class)
	public ResponseEntity<String> handleBlockedContent(BlockedContentException ex) {
		log.warn("Blocked content: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleGenericException(Exception ex) {
		log.error("Unexpected error: {}", ex.getMessage(), ex);
		return ResponseEntity.internalServerError().body("Something went wrong. Please try again.");
	}

}
