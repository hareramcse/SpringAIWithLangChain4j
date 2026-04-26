package com.hs.spring_ai_rag.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;

@Service
public class DataTransformerImpl implements DataTransformer {

	@Override
	public List<TextSegment> transform(List<Document> documents) {
		DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);
		List<TextSegment> segments = new ArrayList<>();
		for (Document doc : documents) {
			segments.addAll(splitter.split(doc));
		}
		return segments;
	}
}
