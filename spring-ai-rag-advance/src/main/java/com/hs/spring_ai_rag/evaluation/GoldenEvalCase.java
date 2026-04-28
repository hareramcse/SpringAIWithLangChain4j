package com.hs.spring_ai_rag.evaluation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One offline evaluation row (golden / smoke expectations).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldenEvalCase(String id, String question, List<String> answerMustContain,
		List<String> answerMustNotContain) {
}
