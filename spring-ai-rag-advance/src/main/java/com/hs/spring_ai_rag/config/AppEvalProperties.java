package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.eval.*}. Evaluation JSON is separate from the ingest corpus so test expectations are never embedded.
 */
@ConfigurationProperties(prefix = "app.eval")
public record AppEvalProperties(
		/** When true, exposes {@code POST /eval/run}. Keep false in production unless secured. */
		boolean httpEnabled,
		/** Classpath or file URL to eval cases (root object with {@code cases} array). */
		String datasetResource) {
}
