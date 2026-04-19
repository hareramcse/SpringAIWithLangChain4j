package com.hs.spring_ai_research.rag;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Hybrid Retriever combining BM25 keyword search with vector similarity search.
 *
 * Uses Reciprocal Rank Fusion (RRF) to merge results from both sources.
 * RRF is simple but effective: score = sum(1 / (k + rank)) for each list.
 *
 * Why hybrid beats either alone:
 * - Vector search finds semantically similar content ("AI safety" matches "model guardrails")
 * - BM25 finds exact keyword matches ("pgvector" matches "pgvector" exactly)
 * - RRF gives high scores to documents that appear highly ranked in BOTH lists
 *
 * The RRF constant k=60 (standard) prevents high-ranked items from dominating too much.
 */
@Slf4j
@Service
public class HybridRetriever {

	private static final int RRF_K = 60;

	private final BM25Retriever bm25Retriever;
	private final EmbeddingModel embeddingModel;
	private final EmbeddingStore<TextSegment> embeddingStore;

	public HybridRetriever(
			BM25Retriever bm25Retriever,
			EmbeddingModel embeddingModel,
			@Qualifier("researchEmbeddingStore") EmbeddingStore<TextSegment> embeddingStore) {
		this.bm25Retriever = bm25Retriever;
		this.embeddingModel = embeddingModel;
		this.embeddingStore = embeddingStore;
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/** Retrieves using both BM25 and vector search, fused with Reciprocal Rank Fusion. */
	public List<TextSegment> retrieve(String query, int maxResults) {
		int retrieveCount = maxResults * 3;

		List<TextSegment> bm25Results = bm25Retriever.search(query, retrieveCount);
		List<TextSegment> vectorResults = vectorSearch(query, retrieveCount);

		List<TextSegment> fused = reciprocalRankFusion(bm25Results, vectorResults, maxResults);

		log.info("Hybrid retrieval: BM25={} + Vector={} -> Fused={} results",
				bm25Results.size(), vectorResults.size(), fused.size());
		return fused;
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	private List<TextSegment> vectorSearch(String query, int maxResults) {
		Embedding queryEmbedding = embeddingModel.embed(query).content();
		EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
				.queryEmbedding(queryEmbedding)
				.maxResults(maxResults)
				.minScore(0.5)
				.build();
		List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
		return matches.stream().map(EmbeddingMatch::embedded).toList();
	}

	/**
	 * Reciprocal Rank Fusion: merges two ranked lists into one.
	 * For each document, RRF score = 1/(k + rank_in_list_A) + 1/(k + rank_in_list_B)
	 */
	private List<TextSegment> reciprocalRankFusion(
			List<TextSegment> listA, List<TextSegment> listB, int maxResults) {

		Map<String, RRFEntry> scoreMap = new HashMap<>();

		for (int i = 0; i < listA.size(); i++) {
			final TextSegment seg = listA.get(i);
			String key = seg.text();
			scoreMap.computeIfAbsent(key, k -> new RRFEntry(seg, 0))
					.addScore(1.0 / (RRF_K + i + 1));
		}

		for (int i = 0; i < listB.size(); i++) {
			final TextSegment seg = listB.get(i);
			String key = seg.text();
			scoreMap.computeIfAbsent(key, k -> new RRFEntry(seg, 0))
					.addScore(1.0 / (RRF_K + i + 1));
		}

		return scoreMap.values().stream()
				.sorted(Comparator.comparingDouble(RRFEntry::getScore).reversed())
				.limit(maxResults)
				.map(RRFEntry::getSegment)
				.toList();
	}

	private static class RRFEntry {
		private final TextSegment segment;
		private double score;

		RRFEntry(TextSegment segment, double score) {
			this.segment = segment;
			this.score = score;
		}

		void addScore(double s) { this.score += s; }
		double getScore() { return score; }
		TextSegment getSegment() { return segment; }
	}
}
