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

	@Value("classpath:static/sample_data.json")
	private Resource jsonResource;

	@Value("classpath:static/cricket_rules.pdf")
	private Resource pdfResource;

	@Override
	public List<Document> loadDocumentsFromJson() {
		log.info("started loading json file data");
		List<Document> documents = new ArrayList<>();
		try {
			ObjectMapper mapper = new ObjectMapper();
			String jsonContent = jsonResource.getContentAsString(StandardCharsets.UTF_8);
			JsonNode rootNode = mapper.readTree(jsonContent);

			if (rootNode.isArray()) {
				for (JsonNode node : rootNode) {
					JsonNode projectNode = node.get("project");
					if (projectNode != null) {
						documents.add(Document.from(projectNode.toString()));
					} else {
						documents.add(Document.from(node.toString()));
					}
				}
			} else {
				documents.add(Document.from(jsonContent));
			}
		} catch (IOException e) {
			log.error("Failed to load JSON data", e);
		}
		return documents;
	}

	@Override
	public List<Document> loadDocumentsFromPdf() {
		log.info("started loading pdf data");
		try (InputStream is = pdfResource.getInputStream()) {
			ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
			Document doc = parser.parse(is);
			return List.of(doc);
		} catch (IOException e) {
			log.error("Failed to load PDF data", e);
			return List.of();
		}
	}
}
