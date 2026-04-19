package com.hs.spring_ai_research.cache;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Semantic caching: caches LLM responses indexed by query embedding.
 *
 * Before making an expensive LLM call, we embed the query and search the cache.
 * If a similar query (cosine similarity > threshold) was asked before,
 * we return the cached response — saving both latency and API cost.
 *
 * This differs from exact-match caching: "What is RAG?" and "Explain RAG to me"
 * would be a cache hit because they're semantically similar.
 */
@Slf4j
@Service
public class SemanticCacheService {

	private final EmbeddingStore<TextSegment> cacheStore;
	private final EmbeddingModel embeddingModel;
	private final double similarityThreshold;

	private final AtomicLong hits = new AtomicLong(0);
	private final AtomicLong misses = new AtomicLong(0);

	public SemanticCacheService(
			@Qualifier("cacheEmbeddingStore") EmbeddingStore<TextSegment> cacheStore,
			EmbeddingModel embeddingModel,
			@Value("${app.cache.similarity-threshold}") double similarityThreshold) {
		this.cacheStore = cacheStore;
		this.embeddingModel = embeddingModel;
		this.similarityThreshold = similarityThreshold;
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Searches for a past query that is semantically similar to the given query.
	 *
	 * @param query the user's current question
	 * @return the cached response if a similar query exists, empty otherwise
	 */
	public Optional<String> lookup(String query) {
		Embedding queryEmbedding = embeddingModel.embed(query).content();
		EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
				.queryEmbedding(queryEmbedding)
				.maxResults(1)
				.minScore(similarityThreshold)
				.build();
		List<EmbeddingMatch<TextSegment>> matches = cacheStore.search(searchRequest).matches();

		if (!matches.isEmpty()) {
			EmbeddingMatch<TextSegment> match = matches.get(0);
			String cachedResponse = match.embedded().metadata().getString("response");
			if (cachedResponse != null) {
				hits.incrementAndGet();
				log.info("CACHE HIT (similarity={}) for query: '{}'",
						String.format("%.4f", match.score()), truncate(query, 50));
				return Optional.of(cachedResponse);
			}
		}

		misses.incrementAndGet();
		log.debug("CACHE MISS for query: '{}'", truncate(query, 50));
		return Optional.empty();
	}

	/**
	 * Stores a query-response pair. The query becomes the vector (for similarity matching),
	 * and the response is stored in metadata (retrieved when there's a hit).
	 */
	public void store(String query, String response) {
		Metadata metadata = new Metadata();
		metadata.put("response", response);
		metadata.put("cached_at", String.valueOf(System.currentTimeMillis()));

		TextSegment segment = TextSegment.from(query, metadata);
		Embedding embedding = embeddingModel.embed(query).content();
		cacheStore.add(embedding, segment);

		log.info("Cached response for query: '{}'", truncate(query, 50));
	}

	// ── Stats ────────────────────────────────────────────────────────────────────

	public CacheStats getStats() {
		return new CacheStats(hits.get(), misses.get());
	}

	public void clearStats() {
		hits.set(0);
		misses.set(0);
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record CacheStats(long hits, long misses) {
		public double hitRate() {
			long total = hits + misses;
			return total == 0 ? 0.0 : (double) hits / total;
		}
	}
}
