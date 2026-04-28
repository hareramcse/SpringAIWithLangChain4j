package com.hs.spring_ai_rag.rag;

import java.util.List;

import com.hs.spring_ai_rag.config.AppRagProperties;

import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * When re-ranking is enabled and the best
 * {@link ContentMetadata#RERANKED_SCORE} among final chunks is below
 * {@link AppRagProperties.Guardrail#minRerankScore()}, strips retrieved
 * contents so the answer model sees no knowledge-base excerpts (see
 * {@link com.hs.spring_ai_rag.service.ChatService} system rules).
 */
@Slf4j
@RequiredArgsConstructor
public final class GatingRetrievalAugmentor implements RetrievalAugmentor {

	private final RetrievalAugmentor delegate;
	private final AppRagProperties rag;

	@Override
	public AugmentationResult augment(AugmentationRequest request) {
		AugmentationResult result = delegate.augment(request);
		var guard = rag.guardrail();
		if (!guard.enabled()) {
			return result;
		}
		if (!rag.reranking().enabled()) {
			return result;
		}

		List<Content> retrievedChunks = result.contents();
		double bestRelevanceScore = maxRelevanceScoreAmong(retrievedChunks);
		if (retrievedChunks.isEmpty() || bestRelevanceScore < guard.minRerankScore()) {
			log.info(
					"RAG guardrail: suppressing answer context (bestRerankScore={}, minRerankScore={}, chunks={}, noEvidenceMessage={}).",
					bestRelevanceScore, guard.minRerankScore(), retrievedChunks.size(), guard.noEvidenceMessage());
			return AugmentationResult.builder().chatMessage(result.chatMessage()).contents(List.of()).build();
		}
		return result;
	}

	/** Same package tests only — not part of the public API. */
	double maxRelevanceScoreAmong(List<Content> chunks) {
		double maxScore = Double.NEGATIVE_INFINITY;
		for (Content chunk : chunks) {
			double score = relevanceScoreFromMetadata(chunk);
			if (score > maxScore) {
				maxScore = score;
			}
		}
		return maxScore == Double.NEGATIVE_INFINITY ? 0.0 : maxScore;
	}

	private double relevanceScoreFromMetadata(Content chunk) {
		Object reranked = chunk.metadata().get(ContentMetadata.RERANKED_SCORE);
		if (reranked instanceof Number n) {
			return n.doubleValue();
		}
		Object retrievalScore = chunk.metadata().get(ContentMetadata.SCORE);
		if (retrievalScore instanceof Number n) {
			return n.doubleValue();
		}
		return 0.0;
	}
}
