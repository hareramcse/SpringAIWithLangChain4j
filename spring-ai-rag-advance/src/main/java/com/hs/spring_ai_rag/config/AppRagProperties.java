package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.rag.*}: retrieval, optional reranking, chunk sizes, guardrail. */
@ConfigurationProperties(prefix = "app.rag")
public record AppRagProperties(Retrieval retrieval, Reranking reranking, Chunking chunking, Guardrail guardrail) {

	public record Retrieval(double minScore, int maxResults, int initialMaxResults) {
	}

	public record Reranking(boolean enabled, int crossEncoderMaxSegmentsPerCall, Double minScore) {
	}

	/** Recursive split only (LangChain4j {@code DocumentSplitters.recursive}). */
	public record Chunking(int maxSegmentSizeChars, int maxOverlapChars) {
	}

	public record Guardrail(boolean enabled, double minRerankScore, String noEvidenceMessage) {
	}
}
