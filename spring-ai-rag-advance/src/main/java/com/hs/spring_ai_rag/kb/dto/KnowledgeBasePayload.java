package com.hs.spring_ai_rag.kb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Root JSON shape for {@code kb/poc-knowledge-base.json}: optional description plus a non-empty {@code documents} array.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeBasePayload(String description, List<KnowledgeDocument> documents) {

	public KnowledgeBasePayload {
		if (documents == null || documents.isEmpty()) {
			throw new IllegalArgumentException("documents must be non-null and non-empty");
		}
	}
}
