package com.hs.spring_ai_research.dto;

/**
 * CONCEPT: Structured Output Extraction
 *
 * Instead of parsing raw JSON strings from the LLM, we define Java POJOs
 * and let LangChain4j's @AiService return them directly. The framework
 * handles JSON schema generation and response parsing automatically.
 *
 * This eliminates fragile manual string parsing and gives compile-time type safety.
 */
public record ReviewScores(
		int faithfulness,
		int relevance,
		int completeness,
		int clarity,
		int hallucinationFree
) {
	public double overall() {
		return (faithfulness + relevance + completeness + clarity + hallucinationFree) / 5.0;
	}
}
