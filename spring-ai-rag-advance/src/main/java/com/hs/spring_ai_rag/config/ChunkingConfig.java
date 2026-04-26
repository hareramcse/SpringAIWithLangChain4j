package com.hs.spring_ai_rag.config;

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
	public DocumentSplitter documentSplitter(AppRagProperties rag) {
		var chunking = rag.chunking();
		DocumentSplitter splitter = buildSplitter(
				chunking.strategy(),
				chunking.maxSegmentSizeChars(),
				chunking.maxOverlapChars());
		log.info(
				"RAG chunking: strategy={}, maxSegmentSizeChars={}, maxOverlapChars={}",
				chunking.strategy(),
				chunking.maxSegmentSizeChars(),
				chunking.maxOverlapChars());
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
