package com.hs.spring_ai_rag.helper;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingStoreHelper {

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;

	public void embedAndStore(List<TextSegment> segments) {
		if (segments.isEmpty()) {
			return;
		}
		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
		embeddingStore.addAll(embeddings, segments);
		log.info("Embedded and stored {} segment(s).", segments.size());
	}

	public boolean hasExistingData() {
		Embedding probe = embeddingModel.embed("test").content();
		var request = EmbeddingSearchRequest.builder()
				.queryEmbedding(probe)
				.maxResults(1)
				.minScore(0.0)
				.build();
		return !embeddingStore.search(request).matches().isEmpty();
	}
}
