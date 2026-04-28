package com.hs.spring_ai_rag.rag.injector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;

class CompressingContentInjectorTest {

	@Test
	void compressByCharBudget_truncatesOrderedChunks() {
		var a = Content.from(TextSegment.from("aaaa"));
		var b = Content.from(TextSegment.from("bbbbbbbb"));
		List<Content> out = CompressingContentInjector.compressByCharBudget(List.of(a, b), 6);
		assertThat(out).hasSize(2);
		assertThat(out.getFirst().textSegment().text()).isEqualTo("aaaa");
		assertThat(out.get(1).textSegment().text()).isEqualTo("bb");
	}
}
