package com.hs.spring_ai_research.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

/**
 * Demonstrates multiple chunking strategies for RAG:
 * - Recursive splitting with configurable size and overlap
 * - Metadata-aware chunking (preserves source info per chunk)
 */
@Slf4j
@Service
public class ChunkingService {

	private static final int DEFAULT_CHUNK_SIZE = 500;
	private static final int DEFAULT_OVERLAP = 50;

	/**
	 * Recursive text splitting — splits by paragraphs, then sentences, then words.
	 * Overlap ensures context isn't lost at chunk boundaries.
	 */
	public List<TextSegment> chunkDocument(Document document, int chunkSize, int overlap) {
		DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, overlap);
		List<TextSegment> segments = splitter.split(document);
		log.info("Chunked document into {} segments (chunkSize={}, overlap={})",
				segments.size(), chunkSize, overlap);
		return segments;
	}

	public List<TextSegment> chunkDocument(Document document) {
		return chunkDocument(document, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
	}

	/**
	 * Chunks raw text with metadata attached to each segment.
	 * Metadata enables filtered vector search (e.g., search only "policy" documents).
	 */
	public List<TextSegment> chunkTextWithMetadata(String text, Map<String, String> metadata) {
		Document doc = Document.from(text, Metadata.from(metadata));
		List<TextSegment> segments = chunkDocument(doc);

		List<TextSegment> enrichedSegments = new ArrayList<>();
		for (int i = 0; i < segments.size(); i++) {
			Metadata segmentMeta = segments.get(i).metadata().copy();
			segmentMeta.put("chunk_index", String.valueOf(i));
			segmentMeta.put("total_chunks", String.valueOf(segments.size()));
			enrichedSegments.add(TextSegment.from(segments.get(i).text(), segmentMeta));
		}
		return enrichedSegments;
	}
}
