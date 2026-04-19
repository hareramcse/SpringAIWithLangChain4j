package com.hs.spring_ai_research.dto;

import java.util.List;

/**
 * CONCEPT: Structured Output Extraction
 *
 * This POJO is returned directly by the StructuredReviewerService (@AiService).
 * LangChain4j automatically:
 * 1. Generates a JSON schema from this record structure
 * 2. Includes the schema in the LLM prompt
 * 3. Parses the LLM's JSON response into this Java record
 *
 * No manual JSON parsing, no string manipulation — fully type-safe.
 */
public record StructuredReview(
		ReviewScores scores,
		double overallScore,
		String verdict,
		List<ReviewIssue> issues,
		String summary
) {}
