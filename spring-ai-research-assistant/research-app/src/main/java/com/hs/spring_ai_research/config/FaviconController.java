package com.hs.spring_ai_research.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browsers request {@code /favicon.ico} by default; without a static file Spring MVC throws
 * {@link org.springframework.web.servlet.resource.NoResourceFoundException}. This mapping runs
 * before classpath resource handlers and answers with 404 without treating it as an error.
 */
@RestController
public class FaviconController {

	@GetMapping("/favicon.ico")
	public ResponseEntity<Void> favicon() {
		return ResponseEntity.notFound().build();
	}
}
