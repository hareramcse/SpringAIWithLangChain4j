package com.hs.spring_ai_research.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Input for the evaluation endpoint ({@code POST /api/eval/run}).
 *
 * @param question the question to evaluate against the knowledge base
 * @param answer   optional answer to score; if blank, only context retrieval is evaluated
 */
public record EvalRequest(
		@NotBlank(message = "Question is required")
		String question,

		String answer
) {}
