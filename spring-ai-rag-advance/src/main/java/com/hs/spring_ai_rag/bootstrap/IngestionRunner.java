package com.hs.spring_ai_rag.bootstrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.config.AppPgVectorProperties;
import com.hs.spring_ai_rag.kb.dto.KnowledgeBasePayload;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;

/**
 * Loads kb when the vector table has no rows.
 */
@Component
@RequiredArgsConstructor
public class IngestionRunner implements CommandLineRunner {

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;
	private final DocumentSplitter documentSplitter;
	private final ObjectMapper objectMapper;
	private final JdbcTemplate jdbcTemplate;
	private final AppPgVectorProperties pg;

	@Value("classpath:kb/poc-knowledge-base.json")
	private Resource knowledgeBaseResource;

	@Override
	public void run(String... args) {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + pg.getTable(), Long.class);
		if (count != null && count > 0) {
			return;
		}

		List<Document> documents = loadDocumentsFromClasspath();

		List<TextSegment> segments = new ArrayList<>();
		for (Document doc : documents) {
			segments.addAll(documentSplitter.split(doc));
		}

		if (segments.isEmpty()) {
			return;
		}
		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
		embeddingStore.addAll(embeddings, segments);
	}

	private List<Document> loadDocumentsFromClasspath() {
		try {
			KnowledgeBasePayload payload = objectMapper.readValue(knowledgeBaseResource.getInputStream(),
					KnowledgeBasePayload.class);
			List<Document> documents = new ArrayList<>(payload.documents().size());
			for (var kbDoc : payload.documents()) {
				documents.add(kbDoc.toLangChain4jDocument());
			}
			return documents;
		} catch (IOException e) {
			throw new IllegalStateException("kb load failed", e);
		}
	}
}
