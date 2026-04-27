package com.hs.spring_ai_rag.service;

import java.util.List;

import dev.langchain4j.data.document.Document;

/**
 * Supplies documents to ingest. The POC uses one structured JSON knowledge base on the classpath.
 */
public interface DataLoader {

	List<Document> loadDocuments();
}
