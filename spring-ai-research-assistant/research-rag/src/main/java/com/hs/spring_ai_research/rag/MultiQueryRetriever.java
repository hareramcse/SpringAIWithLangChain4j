package com.hs.spring_ai_research.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Multi-Query Retrieval: generates multiple query variants from the original query,
 * searches with each variant, then deduplicates results for better recall.
 *
 * This technique overcomes the limitation of single-query retrieval where the
 * embedding of one phrasing might miss relevant documents matched by a different phrasing.
 */
@Slf4j
@Service
public class MultiQueryRetriever {

	private final ChatModel fastModel;
	private final EmbeddingModel embeddingModel;
	private final EmbeddingStore<TextSegment> embeddingStore;

	public MultiQueryRetriever(
			@Qualifier("fastModel") ChatModel fastModel,
			EmbeddingModel embeddingModel,
			@Qualifier("researchEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore) {
		this.fastModel = fastModel;
		this.embeddingModel = embeddingModel;
		this.embeddingStore = embeddingStore;
	}

	public List<TextSegment> retrieve(String originalQuery, int maxResults) {
		List<String> queryVariants = generateQueryVariants(originalQuery);
		log.info("Generated {} query variants for: '{}'", queryVariants.size(), originalQuery);

		Map<String, TextSegment> deduplicated = new LinkedHashMap<>();

		for (String query : queryVariants) {
			Embedding queryEmbedding = embeddingModel.embed(query).content();
			EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
					.queryEmbedding(queryEmbedding)
					.maxResults(maxResults)
					.minScore(0.5)
					.build();
			List<EmbeddingMatch<TextSegment>> matches =
					embeddingStore.search(searchRequest).matches();

			for (EmbeddingMatch<TextSegment> match : matches) {
				String text = match.embedded().text();
				deduplicated.putIfAbsent(text, match.embedded());
			}
		}

		List<TextSegment> results = new ArrayList<>(deduplicated.values());
		log.info("Multi-query retrieval returned {} unique segments", results.size());
		return results;
	}

	private List<String> generateQueryVariants(String originalQuery) {
		String prompt = """
				Generate 3 different versions of the following search query.
				Each version should rephrase the question from a different angle
				to help find relevant documents. Return ONLY the 3 queries,
				one per line, no numbering, no extra text.

				Original query: %s""".formatted(originalQuery);

		String response = fastModel.chat(prompt);
		List<String> variants = new ArrayList<>();
		variants.add(originalQuery);
		for (String line : response.split("\n")) {
			String trimmed = line.trim();
			if (!trimmed.isEmpty()) {
				variants.add(trimmed);
			}
		}
		return variants;
	}
}
