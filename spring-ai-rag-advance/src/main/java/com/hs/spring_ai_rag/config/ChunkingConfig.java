package com.hs.spring_ai_rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hs.spring_ai_rag.chunking.ChunkingStrategy;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ChunkingConfig {

	@Bean
	public DocumentSplitter documentSplitter(
			@Value("${app.rag.chunking.strategy}") ChunkingStrategy strategy,
			@Value("${app.rag.chunking.max-segment-size-chars}") int maxSegmentSizeChars,
			@Value("${app.rag.chunking.max-overlap-chars}") int maxOverlapChars) {
		DocumentSplitter splitter = buildSplitter(strategy, maxSegmentSizeChars, maxOverlapChars);
		log.info("RAG chunking: strategy={}, maxSegmentSizeChars={}, maxOverlapChars={}",
				strategy, maxSegmentSizeChars, maxOverlapChars);
		return splitter;
	}

	private static DocumentSplitter buildSplitter(ChunkingStrategy strategy, int max, int overlap) {
		DocumentSplitter recursiveFallback = DocumentSplitters.recursive(max, overlap);
		return switch (strategy) {
			case RECURSIVE -> recursiveFallback;
			case PARAGRAPH -> new DocumentByParagraphSplitter(max, overlap, recursiveFallback);
			case SENTENCE -> new DocumentBySentenceSplitter(max, overlap, recursiveFallback);
			case LINE -> new DocumentByLineSplitter(max, overlap, recursiveFallback);
			case WORD -> new DocumentByWordSplitter(max, overlap, recursiveFallback);
			case CHARACTER -> new DocumentByCharacterSplitter(max, overlap, recursiveFallback);
		};
	}
}
