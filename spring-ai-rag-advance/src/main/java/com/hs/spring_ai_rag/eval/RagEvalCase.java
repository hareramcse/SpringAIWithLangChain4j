package com.hs.spring_ai_rag.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row in the golden JSON: query plus substrings that must appear in retrieved context and in the final answer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RagEvalCase(
		String id,
		String query,
		/** Must appear in at least one retrieved chunk after the same {@link dev.langchain4j.rag.RetrievalAugmentor} path as chat. */
		String expectedDocumentContains,
		/** Every non-blank phrase must appear in the model answer (substring, case-insensitive). If null or empty, answer is not scored. */
		List<String> expectedAnswerContains) {

	public RagEvalCase {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("eval case id is required");
		}
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("eval case query is required for id=" + id);
		}
		if (expectedDocumentContains == null || expectedDocumentContains.isBlank()) {
			throw new IllegalArgumentException("expectedDocumentContains is required for id=" + id);
		}
	}

	public boolean hasAnswerCriteria() {
		return expectedAnswerContains != null
				&& expectedAnswerContains.stream().anyMatch(p -> p != null && !p.isBlank());
	}
}
