package com.hs.spring_ai_research.agent;

import com.hs.spring_ai_research.dto.StructuredReview;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * CONCEPT: Structured Output Extraction via @AiService
 *
 * This is the declarative approach to AI services in LangChain4j.
 * By returning a Java record (StructuredReview) instead of String,
 * the framework automatically:
 * - Adds a JSON schema to the prompt so the LLM knows the expected structure
 * - Parses the LLM response into the StructuredReview record
 * - Throws if the response doesn't match the schema
 *
 * Compare this with ReviewerAgent.java which uses manual string parsing —
 * this approach is cleaner, safer, and less error-prone.
 *
 * wiringMode = EXPLICIT is required when multiple ChatModel beans exist.
 * We pick "powerfulModel" because structured output needs strong reasoning.
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "powerfulModel")
public interface StructuredReviewerService {

	@SystemMessage("""
			You are a Senior Research Reviewer. Evaluate the report against these criteria (1-10 scale):
			- faithfulness: Is every claim grounded in the sources?
			- relevance: Does the report answer the question?
			- completeness: Are all available sources utilized?
			- clarity: Is the report well-structured and clear?
			- hallucinationFree: Does the report avoid fabricated information?

			Verdict rules:
			- PASS: overallScore >= 7.0 and no individual score below 5
			- NEEDS_REVISION: overallScore >= 5.0 or any score between 3-5
			- FAIL: overallScore < 5.0 or any score below 3
			""")
	@UserMessage("""
			Review this research report:

			Original Question: {{question}}

			Research Findings (Sources): {{findings}}

			Report: {{report}}
			""")
	StructuredReview review(String question, String findings, String report);
}
