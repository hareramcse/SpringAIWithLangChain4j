package com.hs.spring_ai_rag.rag;

import java.util.List;

import com.hs.spring_ai_rag.config.AppRagProperties;

import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import lombok.RequiredArgsConstructor;

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
			return AugmentationResult.builder().chatMessage(result.chatMessage()).contents(List.of()).build();
		}
		return result;
	}

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
