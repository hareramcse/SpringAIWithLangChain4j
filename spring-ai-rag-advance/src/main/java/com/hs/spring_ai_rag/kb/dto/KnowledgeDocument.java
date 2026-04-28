package com.hs.spring_ai_rag.kb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;

/**
 * One logical article from {@code kb/*.json} — maps directly to JSON fields {@code id}, {@code title}, {@code body}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeDocument(String id, String title, String body) {

	/**
	 * Builds the markdown text and metadata LangChain4j uses for chunking and retrieval ({@code kb_id} in metadata).
	 */
	public Document toLangChain4jDocument() {
		String markdown = "# " + title + "\n\n" + body;
		return Document.from(markdown, Metadata.metadata("kb_id", id));
	}
}
