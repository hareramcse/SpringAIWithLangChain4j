package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hs.spring_ai_rag.chunking.ChunkingStrategy;

/**
 * Binds {@code app.rag.*}: retrieval, reranking, chunking, and optional post-rank guardrail.
 */
@ConfigurationProperties(prefix = "app.rag")
public record AppRagProperties(Retrieval retrieval, Reranking reranking, Chunking chunking, Guardrail guardrail) {

	public record Retrieval(double minScore, int maxResults, int initialMaxResults) {
	}

	public record Reranking(
			boolean enabled,
			int crossEncoderMaxSegmentsPerCall,
			int rerankCandidateCap,
			boolean mmrEnabled,
			double mmrLambda,
			/** Present only when {@code app.rag.reranking.min-score} is set. */
			Double minScore) {
	}

	public record Chunking(ChunkingStrategy strategy, int maxSegmentSizeChars, int maxOverlapChars) {
	}

	/**
	 * Post re-rank confidence gate: if the best {@code RERANKED_SCORE} among selected chunks is below
	 * {@link #minRerankScore()}, no excerpts are passed to the chat model.
	 */
	public record Guardrail(boolean enabled, double minRerankScore, String noEvidenceMessage) {
	}
}
