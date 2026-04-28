package com.hs.spring_ai_rag.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;

class GatingRetrievalAugmentorTest {

	@Test
	void maxRelevanceScoreAmong_prefersRerankedScoreOverRetrievalScore() {
		var augmentor = new GatingRetrievalAugmentor(null, null);
		var lowerRankedChunk = Content.from(
				TextSegment.from("a"),
				Map.of(ContentMetadata.RERANKED_SCORE, 0.1, ContentMetadata.SCORE, 0.99));
		var higherRankedChunk = Content.from(TextSegment.from("b"), Map.of(ContentMetadata.RERANKED_SCORE, 0.4));
		assertThat(augmentor.maxRelevanceScoreAmong(List.of(lowerRankedChunk, higherRankedChunk))).isEqualTo(0.4);
	}

	@Test
	void maxRelevanceScoreAmong_emptyList_returnsZero() {
		var augmentor = new GatingRetrievalAugmentor(null, null);
		assertThat(augmentor.maxRelevanceScoreAmong(List.of())).isZero();
	}
}
