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
	void maxRerankedScore_prefersRerankedOverScore() {
		var low = Content.from(
				TextSegment.from("a"),
				Map.of(ContentMetadata.RERANKED_SCORE, 0.1, ContentMetadata.SCORE, 0.99));
		var high = Content.from(TextSegment.from("b"), Map.of(ContentMetadata.RERANKED_SCORE, 0.4));
		assertThat(GatingRetrievalAugmentor.maxRerankedScore(List.of(low, high))).isEqualTo(0.4);
	}

	@Test
	void maxRerankedScore_emptyList() {
		assertThat(GatingRetrievalAugmentor.maxRerankedScore(List.of())).isZero();
	}
}
