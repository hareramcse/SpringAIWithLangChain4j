package com.hs.spring_ai_rag.helper;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EmbeddingStoreHelper {

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;

	public EmbeddingStoreHelper(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
		this.embeddingStore = embeddingStore;
		this.embeddingModel = embeddingModel;
	}

	public void embedAndStore(List<TextSegment> segments) {
		for (TextSegment segment : segments) {
			var embedding = embeddingModel.embed(segment).content();
			embeddingStore.add(embedding, segment);
		}
		log.info("Embedded and stored {} text segments.", segments.size());
	}

	public boolean hasExistingData() {
		var probe = embeddingModel.embed("test").content();
		var request = EmbeddingSearchRequest.builder()
				.queryEmbedding(probe)
				.maxResults(1)
				.minScore(0.0)
				.build();
		return !embeddingStore.search(request).matches().isEmpty();
	}

}
