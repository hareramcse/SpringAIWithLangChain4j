package com.hs.spring_ai_rag.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DataLoaderImpl implements DataLoader {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Value("classpath:static/sample_data.json")
	private Resource jsonResource;

	@Value("classpath:static/cricket_rules.pdf")
	private Resource pdfResource;

	@Override
	public List<Document> loadDocumentsFromJson() {
		log.info("Loading JSON documents");
		try {
			String jsonContent = jsonResource.getContentAsString(StandardCharsets.UTF_8);
			JsonNode root = JSON.readTree(jsonContent);
			return documentsFromJsonRoot(root, jsonContent);
		} catch (IOException e) {
			log.error("Failed to load JSON data", e);
			return List.of();
		}
	}

	@Override
	public List<Document> loadDocumentsFromPdf() {
		log.info("Loading PDF document");
		try (InputStream inputStream = pdfResource.getInputStream()) {
			ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
			return List.of(parser.parse(inputStream));
		} catch (IOException e) {
			log.error("Failed to load PDF data", e);
			return List.of();
		}
	}

	private static List<Document> documentsFromJsonRoot(JsonNode root, String rawJson) {
		List<Document> documents = new ArrayList<>();
		if (root.isArray()) {
			for (JsonNode node : root) {
				documents.add(documentFromJsonNode(node));
			}
		} else {
			documents.add(Document.from(rawJson));
		}
		return documents;
	}

	private static Document documentFromJsonNode(JsonNode node) {
		JsonNode project = node.get("project");
		if (project != null) {
			return Document.from(project.toString());
		}
		return Document.from(node.toString());
	}
}
