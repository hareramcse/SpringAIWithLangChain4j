package com.hs.spring_ai_help_desk.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

	private final EmbeddingStore<TextSegment> embeddingStore;
	private final EmbeddingModel embeddingModel;
	private final ContentRetriever contentRetriever;

	private final DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);

	@EventListener(ApplicationReadyEvent.class)
	public void loadDefaultKnowledgeBase() {
		if (hasExistingData()) {
			log.info("Knowledge base already contains data, skipping initial load.");
			return;
		}

		log.info("Loading default knowledge base documents...");
		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			Resource[] resources = resolver.getResources("classpath:knowledge-base/*");

			int totalSegments = 0;
			for (Resource resource : resources) {
				String filename = resource.getFilename();
				log.info("Ingesting knowledge base file: {}", filename);
				List<TextSegment> segments = ingestResource(resource, filename);
				totalSegments += segments.size();
			}

			log.info("Knowledge base loaded: {} total segments from {} files.", totalSegments, resources.length);
		} catch (IOException e) {
			log.error("Failed to load default knowledge base", e);
		}
	}

	public List<TextSegment> ingestResource(Resource resource, String sourceName) {
		try (InputStream is = resource.getInputStream()) {
			Document document;
			if (sourceName != null && sourceName.endsWith(".pdf")) {
				document = new ApacheTikaDocumentParser().parse(is);
			} else {
				String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
				document = Document.from(content, Metadata.from("source", sourceName));
			}
			document.metadata().put("source", sourceName);

			List<TextSegment> segments = splitter.split(document);
			embedAndStore(segments);

			log.info("Ingested '{}': {} segments.", sourceName, segments.size());
			return segments;
		} catch (IOException e) {
			log.error("Failed to ingest resource: {}", sourceName, e);
			return List.of();
		}
	}

	public List<TextSegment> ingestText(String text, String sourceName) {
		Document document = Document.from(text, Metadata.from("source", sourceName));
		List<TextSegment> segments = splitter.split(document);
		embedAndStore(segments);
		log.info("Ingested text '{}': {} segments.", sourceName, segments.size());
		return segments;
	}

	public String search(String query, int maxResults) {
		List<Content> results = contentRetriever.retrieve(new Query(query));

		if (results.isEmpty()) {
			return "No relevant information found in the knowledge base for: " + query;
		}

		return results.stream()
				.limit(maxResults)
				.map(content -> content.textSegment().text())
				.collect(Collectors.joining("\n\n---\n\n",
						"Knowledge Base Results (" + Math.min(results.size(), maxResults) + " matches):\n\n",
						""));
	}

	private void embedAndStore(List<TextSegment> segments) {
		List<Embedding> embeddings = new ArrayList<>();
		for (TextSegment segment : segments) {
			embeddings.add(embeddingModel.embed(segment).content());
		}
		embeddingStore.addAll(embeddings, segments);
	}

	private boolean hasExistingData() {
		try {
			Embedding probe = embeddingModel.embed("test").content();
			EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
					.queryEmbedding(probe)
					.maxResults(1)
					.minScore(0.0)
					.build();
			EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
			return !result.matches().isEmpty();
		} catch (Exception e) {
			log.warn("Could not check for existing data: {}", e.getMessage());
			return false;
		}
	}

}
