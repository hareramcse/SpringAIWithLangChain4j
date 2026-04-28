package com.hs.spring_ai_rag.bootstrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.kb.dto.KnowledgeBasePayload;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * If the vector table is empty: load `kb/poc-knowledge-base.json`, split,
 * embed, store.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionRunner implements CommandLineRunner {

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;
	private final DocumentSplitter documentSplitter;
	private final ObjectMapper objectMapper;

	@Value("classpath:kb/poc-knowledge-base.json")
	private Resource knowledgeBaseResource;

	@Override
	public void run(String... args) {
		List<Document> documents = loadDocumentsFromClasspath();
		log.info("Loaded {} document(s).", documents.size());

		List<TextSegment> segments = new ArrayList<>();
		for (Document doc : documents) {
			segments.addAll(documentSplitter.split(doc));
		}
		log.info("Split into {} segment(s).", segments.size());

		if (segments.isEmpty()) {
			return;
		}
		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
		embeddingStore.addAll(embeddings, segments);
		log.info("Embedded and stored {} segment(s). Ingest done.", segments.size());
	}

	private List<Document> loadDocumentsFromClasspath() {
		log.info("Loading knowledge base: {}", knowledgeBaseResource);
		try {
			KnowledgeBasePayload payload = objectMapper.readValue(knowledgeBaseResource.getInputStream(),
					KnowledgeBasePayload.class);
			List<Document> documents = new ArrayList<>(payload.documents().size());
			for (var kbDoc : payload.documents()) {
				documents.add(kbDoc.toLangChain4jDocument());
			}
			if (payload.description() != null && !payload.description().isBlank()) {
				log.info("Knowledge base description: {}", payload.description());
			}
			log.info("Parsed {} knowledge document(s).", documents.size());
			return documents;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load knowledge base", e);
		}
	}
}
