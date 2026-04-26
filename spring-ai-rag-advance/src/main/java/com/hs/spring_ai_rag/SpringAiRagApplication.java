package com.hs.spring_ai_rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.hs.spring_ai_rag.helper.EmbeddingStoreHelper;
import com.hs.spring_ai_rag.service.DataLoader;
import com.hs.spring_ai_rag.service.DataTransformer;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class SpringAiRagApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiRagApplication.class, args);
	}

	@Bean
	CommandLineRunner loadData(EmbeddingStoreHelper embeddingStoreHelper, DataLoader dataLoader,
			DataTransformer dataTransformer) {
		return args -> {
			if (embeddingStoreHelper.hasExistingData()) {
				log.info("Embedding store already has data, skipping re-embedding.");
				return;
			}

			log.info("Loading documents from PDF and JSON...");
			List<Document> allDocuments = new ArrayList<>();
			allDocuments.addAll(dataLoader.loadDocumentsFromPdf());
			allDocuments.addAll(dataLoader.loadDocumentsFromJson());
			log.info("Loaded {} documents.", allDocuments.size());

			List<TextSegment> segments = dataTransformer.transform(allDocuments);
			log.info("Split into {} text segments.", segments.size());

			embeddingStoreHelper.embedAndStore(segments);
			log.info("Data pipeline complete.");
		};
	}

}
