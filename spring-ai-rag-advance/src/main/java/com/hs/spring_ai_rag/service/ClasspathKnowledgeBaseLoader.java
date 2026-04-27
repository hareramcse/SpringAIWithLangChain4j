package com.hs.spring_ai_rag.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.knowledge.KnowledgeBaseResources;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads {@code kb/poc-knowledge-base.json}: each entry becomes one {@link Document} with a stable {@code kb_id}
 * in metadata for debugging and evaluation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClasspathKnowledgeBaseLoader implements DataLoader {

	private final ObjectMapper objectMapper;

	@Value(KnowledgeBaseResources.POC_CLASSPATH)
	private Resource knowledgeBaseResource;

	@Override
	public List<Document> loadDocuments() {
		log.info("Loading classpath knowledge base: {}", knowledgeBaseResource);
		try {
			JsonNode root = objectMapper.readTree(knowledgeBaseResource.getInputStream());
			JsonNode docs = root.required("documents");
			if (!docs.isArray() || docs.isEmpty()) {
				throw new IllegalStateException("knowledge base must contain a non-empty 'documents' array");
			}
			List<Document> out = new ArrayList<>();
			for (JsonNode node : docs) {
				String id = node.required("id").asText();
				String title = node.required("title").asText();
				String body = node.required("body").asText();
				String text = "# " + title + "\n\n" + body;
				Metadata meta = Metadata.metadata("kb_id", id);
				out.add(Document.from(text, meta));
			}
			log.info("Parsed {} knowledge document(s).", out.size());
			return out;
		} catch (IOException e) {
			log.error("Failed to load knowledge base", e);
			return List.of();
		}
	}
}
