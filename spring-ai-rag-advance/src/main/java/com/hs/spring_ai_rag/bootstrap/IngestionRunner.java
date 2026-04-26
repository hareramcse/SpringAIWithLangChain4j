package com.hs.spring_ai_rag.bootstrap;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.hs.spring_ai_rag.helper.EmbeddingStoreHelper;
import com.hs.spring_ai_rag.service.DataLoader;
import com.hs.spring_ai_rag.service.DataTransformer;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads PDF/JSON from the classpath, splits into segments, and embeds into the store when it is empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionRunner implements CommandLineRunner {

	private final EmbeddingStoreHelper embeddingStoreHelper;
	private final DataLoader dataLoader;
	private final DataTransformer dataTransformer;

	@Override
	public void run(String... args) {
		if (embeddingStoreHelper.hasExistingData()) {
			log.info("Embedding store already has data; skipping ingest.");
			return;
		}

		List<Document> documents = loadAllDocuments();
		log.info("Loaded {} document(s).", documents.size());

		List<TextSegment> segments = dataTransformer.transform(documents);
		log.info("Split into {} segment(s).", segments.size());

		embeddingStoreHelper.embedAndStore(segments);
		log.info("Ingest pipeline finished.");
	}

	private List<Document> loadAllDocuments() {
		List<Document> all = new ArrayList<>();
		all.addAll(dataLoader.loadDocumentsFromPdf());
		all.addAll(dataLoader.loadDocumentsFromJson());
		return all;
	}
}
