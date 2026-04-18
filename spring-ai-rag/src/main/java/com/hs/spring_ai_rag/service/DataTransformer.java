package com.hs.spring_ai_rag.service;

import java.util.List;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

public interface DataTransformer {

	List<TextSegment> transform(List<Document> documents);

}
