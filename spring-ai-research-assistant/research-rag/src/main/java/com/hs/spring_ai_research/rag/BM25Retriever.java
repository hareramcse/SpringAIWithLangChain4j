package com.hs.spring_ai_research.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;


import org.springframework.stereotype.Service;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory BM25 (Best Matching 25) keyword retriever.
 *
 * BM25 is the classic information retrieval algorithm used by Elasticsearch and Lucene.
 * It ranks documents by term frequency (TF) and inverse document frequency (IDF),
 * with diminishing returns for repeated terms.
 *
 * Why BM25 alongside vector search?
 * - Vector search excels at semantic similarity ("what does it mean?")
 * - BM25 excels at exact keyword matching ("find this specific term")
 * - Combined (hybrid), they cover both angles and improve recall significantly
 *
 * Formula: score(D,Q) = sum over q in Q of: IDF(q) * (f(q,D) * (k1+1)) / (f(q,D) + k1 * (1 - b + b * |D|/avgdl))
 * Where k1=1.5, b=0.75 are tuning parameters.
 */
@Slf4j
@Service
public class BM25Retriever {

	private static final double K1 = 1.5;
	private static final double B = 0.75;
	private static final Set<String> STOP_WORDS = Set.of(
			"the", "a", "an", "is", "are", "was", "were", "in", "on", "at",
			"to", "for", "of", "with", "by", "from", "and", "or", "not", "it",
			"this", "that", "be", "has", "have", "had", "do", "does", "did",
			"will", "would", "could", "should", "can", "may", "might");

	private final CopyOnWriteArrayList<IndexedDocument> index = new CopyOnWriteArrayList<>();
	private final Map<String, Integer> documentFrequency = new HashMap<>();
	private double averageDocLength = 0;

	// ── Indexing ─────────────────────────────────────────────────────────────────

	/** Adds segments to the BM25 inverted index. Called during document ingestion. */
	public synchronized void addToIndex(List<TextSegment> segments) {
		for (TextSegment segment : segments) {
			List<String> tokens = tokenize(segment.text());
			index.add(new IndexedDocument(segment, tokens));

			Set<String> uniqueTokens = new HashSet<>(tokens);
			for (String token : uniqueTokens) {
				documentFrequency.merge(token, 1, Integer::sum);
			}
		}
		averageDocLength = index.stream().mapToInt(d -> d.tokens.size()).average().orElse(1.0);
		log.debug("BM25 index updated: {} total documents, avg length {}", index.size(),
				String.format("%.1f", averageDocLength));
	}

	// ── Search ───────────────────────────────────────────────────────────────────

	/** Returns top-K results ranked by BM25 keyword relevance score. */
	public List<TextSegment> search(String query, int maxResults) {
		if (index.isEmpty()) {
			return List.of();
		}

		List<String> queryTokens = tokenize(query);
		int totalDocs = index.size();

		List<ScoredDocument> scored = new ArrayList<>();
		for (IndexedDocument doc : index) {
			double score = computeBM25Score(queryTokens, doc, totalDocs);
			if (score > 0) {
				scored.add(new ScoredDocument(doc.segment, score));
			}
		}

		scored.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
		List<TextSegment> results = scored.stream()
				.limit(maxResults)
				.map(ScoredDocument::segment)
				.toList();

		log.debug("BM25 search for '{}': {} results from {} candidates",
				query, results.size(), scored.size());
		return results;
	}

	public int getIndexSize() {
		return index.size();
	}

	// ── BM25 scoring ────────────────────────────────────────────────────────────

	private double computeBM25Score(List<String> queryTokens, IndexedDocument doc, int totalDocs) {
		double score = 0;
		Map<String, Integer> termFreqs = new HashMap<>();
		for (String token : doc.tokens) {
			termFreqs.merge(token, 1, Integer::sum);
		}

		for (String queryToken : queryTokens) {
			int tf = termFreqs.getOrDefault(queryToken, 0);
			if (tf == 0) continue;

			int df = documentFrequency.getOrDefault(queryToken, 0);
			double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

			double numerator = tf * (K1 + 1);
			double denominator = tf + K1 * (1 - B + B * doc.tokens.size() / averageDocLength);
			score += idf * (numerator / denominator);
		}
		return score;
	}

	private List<String> tokenize(String text) {
		List<String> tokens = new ArrayList<>();
		for (String word : text.toLowerCase().split("\\W+")) {
			if (word.length() > 1 && !STOP_WORDS.contains(word)) {
				tokens.add(word);
			}
		}
		return tokens;
	}

	private record IndexedDocument(TextSegment segment, List<String> tokens) {}

	private record ScoredDocument(TextSegment segment, double score) {}
}
