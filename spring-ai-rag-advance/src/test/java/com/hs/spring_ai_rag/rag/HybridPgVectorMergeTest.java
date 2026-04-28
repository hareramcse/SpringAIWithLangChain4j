package com.hs.spring_ai_rag.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;

class HybridPgVectorMergeTest {

	@Test
	void mergeDistinct_prefersVectorOrderAndDedupesByText() {
		var v1 = Content.from(TextSegment.from("same"));
		var v2 = Content.from(TextSegment.from("only-vector"));
		var k1 = Content.from(TextSegment.from("same"));
		var k2 = Content.from(TextSegment.from("only-fts"));
		List<Content> merged = HybridPgVectorContentRetriever.mergeDistinctChunkTextPreservingVectorFirst(
				List.of(v1, v2), List.of(k1, k2));
		assertThat(merged).hasSize(3);
		assertThat(merged.getFirst().textSegment().text()).isEqualTo("same");
		assertThat(merged.get(1).textSegment().text()).isEqualTo("only-vector");
		assertThat(merged.get(2).textSegment().text()).isEqualTo("only-fts");
	}
}
