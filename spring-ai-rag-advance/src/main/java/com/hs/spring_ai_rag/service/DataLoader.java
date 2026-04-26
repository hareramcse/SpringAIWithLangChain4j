package com.hs.spring_ai_rag.service;

import java.util.List;

import dev.langchain4j.data.document.Document;

public interface DataLoader {

	List<Document> loadDocumentsFromJson();

	List<Document> loadDocumentsFromPdf();

}
