package com.hs.spring_ai_rag.chunking;

/**
 * Selectable document chunking strategies for the ingestion pipeline.
 * Each value maps to a LangChain4j {@link dev.langchain4j.data.document.DocumentSplitter}
 * configured in {@link com.hs.spring_ai_rag.config.ChunkingConfig}.
 */
public enum ChunkingStrategy {

	/**
	 * Recursive split on natural boundaries (default), then by size with overlap.
	 */
	RECURSIVE,

	/**
	 * Split by paragraphs first; oversized blocks are subdivided with the configured sub-splitter.
	 */
	PARAGRAPH,

	/**
	 * Split by sentences first; oversized blocks are subdivided.
	 */
	SENTENCE,

	/**
	 * Split by lines first; oversized blocks are subdivided.
	 */
	LINE,

	/**
	 * Split by words first; oversized blocks are subdivided.
	 */
	WORD,

	/**
	 * Fixed-size character windows with overlap (hierarchical character splitter).
	 */
	CHARACTER
}
