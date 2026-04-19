package com.hs.spring_ai_research.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import org.springframework.stereotype.Service;

import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the full production-grade RAG pipeline:
 *
 * 1. Query Planning — rewrite ambiguous queries, decompose complex ones
 * 2. Hybrid Retrieval — BM25 keyword + vector semantic search with RRF fusion
 * 3. Metadata Filtering — tenant, category, time, source filters
 * 4. Re-Ranking — LLM scores each chunk's relevance
 * 5. Contextual Compression — extract only relevant sentences from top chunks
 *
 * This pipeline addresses the key failure modes of naive RAG:
 * - Vague queries miss relevant docs -> Query Planning fixes this
 * - Semantic gap (different terminology) -> BM25 catches exact keywords
 * - Irrelevant chunks dilute context -> Re-ranking + Compression fix this
 * - Multi-tenant leakage -> Metadata filtering prevents it
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedRagService {

	private final MultiQueryRetriever multiQueryRetriever;
	private final HybridRetriever hybridRetriever;
	private final QueryPlanner queryPlanner;
	private final MetadataFilter metadataFilter;
	private final ReRankingService reRankingService;
	private final ContextualCompressor contextualCompressor;

	private static final int INITIAL_RETRIEVAL_COUNT = 15;
	private static final int RERANK_TOP_K = 5;

	// ── Full pipeline ───────────────────────────────────────────────────────────

	/** Full pipeline with no metadata filters. */
	public List<TextSegment> retrieve(String query) {
		return retrieve(query, MetadataFilter.FilterCriteria.none());
	}

	public List<TextSegment> retrieve(String query, MetadataFilter.FilterCriteria filters) {
		log.info("=== Advanced RAG Pipeline START for query: '{}' ===", query);

		// Step 1: Query Planning
		QueryPlanner.QueryPlan plan = queryPlanner.plan(query);
		log.info("Step 1 - Query Plan: rewritten='{}', subQueries={}", plan.rewrittenQuery(), plan.subQueries().size());

		// Step 2: Hybrid Retrieval (BM25 + Vector) for each sub-query
		Map<String, TextSegment> deduplicated = new LinkedHashMap<>();
		for (String subQuery : plan.subQueries()) {
			List<TextSegment> hybridResults = hybridRetriever.retrieve(subQuery, INITIAL_RETRIEVAL_COUNT);
			for (TextSegment seg : hybridResults) {
				deduplicated.putIfAbsent(seg.text(), seg);
			}
		}
		List<TextSegment> retrieved = new ArrayList<>(deduplicated.values());
		log.info("Step 2 - Hybrid Retrieval: {} unique segments from {} sub-queries",
				retrieved.size(), plan.subQueries().size());

		if (retrieved.isEmpty()) {
			log.warn("No segments retrieved — pipeline returning empty");
			return retrieved;
		}

		// Step 3: Metadata Filtering
		retrieved = metadataFilter.applyFilters(retrieved, filters);
		log.info("Step 3 - Metadata Filtering: {} segments after filters", retrieved.size());

		if (retrieved.isEmpty()) {
			return retrieved;
		}

		// Step 4: Re-Ranking
		List<TextSegment> reRanked = reRankingService.reRank(query, retrieved, RERANK_TOP_K);
		log.info("Step 4 - Re-Ranking: {} segments", reRanked.size());

		// Step 5: Contextual Compression
		List<TextSegment> compressed = contextualCompressor.compress(query, reRanked);
		log.info("Step 5 - Contextual Compression: {} segments", compressed.size());

		log.info("=== Advanced RAG Pipeline END: {} -> {} -> {} segments ===",
				retrieved.size(), reRanked.size(), compressed.size());
		return compressed;
	}

	// ── Evaluation helpers ───────────────────────────────────────────────────────

	/** Retrieval without compression — used by eval pipelines that need raw chunks. */
	public List<TextSegment> retrieveWithoutCompression(String query) {
		Map<String, TextSegment> deduplicated = new LinkedHashMap<>();
		List<TextSegment> hybridResults = hybridRetriever.retrieve(query, INITIAL_RETRIEVAL_COUNT);
		for (TextSegment seg : hybridResults) {
			deduplicated.putIfAbsent(seg.text(), seg);
		}
		List<TextSegment> retrieved = new ArrayList<>(deduplicated.values());
		return reRankingService.reRank(query, retrieved, RERANK_TOP_K);
	}

	/** Formats retrieved segments into a single context string, with source annotations. */
	public String formatAsContext(List<TextSegment> segments) {
		return segments.stream()
				.map(s -> {
					String source = s.metadata().getString("source");
					String sourceInfo = source != null ? " [Source: " + source + "]" : "";
					return s.text() + sourceInfo;
				})
				.collect(Collectors.joining("\n\n---\n\n"));
	}
}
