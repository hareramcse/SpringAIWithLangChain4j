package com.hs.spring_ai_rag.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;

@Service
public class DataTransformerImpl implements DataTransformer {

	private final DocumentSplitter documentSplitter;

	public DataTransformerImpl(DocumentSplitter documentSplitter) {
		this.documentSplitter = documentSplitter;
	}

	@Override
	public List<TextSegment> transform(List<Document> documents) {
		List<TextSegment> segments = new ArrayList<>();
		for (Document doc : documents) {
			segments.addAll(documentSplitter.split(doc));
		}
		return segments;
	}
}
