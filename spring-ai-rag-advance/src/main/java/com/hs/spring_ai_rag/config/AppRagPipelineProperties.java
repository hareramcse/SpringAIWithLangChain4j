package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Optional RAG pipeline stages: query normalization before retrieval, character-budget context compression before injection.
 */
@ConfigurationProperties(prefix = "app.rag.pipeline")
@Getter
@Setter
public class AppRagPipelineProperties {

	/** Trim and collapse whitespace on the user query before hybrid search. */
	private boolean queryNormalizationEnabled = true;

	/**
	 * Cap total injected retrieval text (approximate context window budget). Applied after re-ranking, before the LLM.
	 */
	private boolean contextCompressionEnabled = true;

	/** Max combined character length of retrieved excerpts injected into the prompt. */
	private int maxInjectedContextChars = 1800;
}
