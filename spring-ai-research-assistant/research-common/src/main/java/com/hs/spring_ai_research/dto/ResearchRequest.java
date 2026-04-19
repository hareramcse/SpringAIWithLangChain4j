package com.hs.spring_ai_research.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input for the main research endpoint ({@code POST /api/research}).
 *
 * @param question  the research question (5-2000 characters)
 * @param skipCache if true, bypasses semantic cache and forces a fresh LLM call
 */
public record ResearchRequest(
		@NotBlank(message = "Question is required")
		@Size(min = 5, max = 2000, message = "Question must be between 5 and 2000 characters")
		String question,

		boolean skipCache
) {}
