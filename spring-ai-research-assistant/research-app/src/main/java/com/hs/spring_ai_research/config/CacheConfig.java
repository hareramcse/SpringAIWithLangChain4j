package com.hs.spring_ai_research.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates a separate PGVector embedding store dedicated to semantic caching.
 *
 * <p>This is intentionally a different table from the knowledge base store.
 * The cache stores query-response pairs; the knowledge base stores document chunks.
 * Keeping them separate avoids polluting search results with cached queries.</p>
 *
 * @see com.hs.spring_ai_research.cache.SemanticCacheService
 */
@Slf4j
@Configuration
public class CacheConfig {

	/** Stores past queries as vectors — used by SemanticCacheService to find similar past queries. */
	@Bean("cacheEmbeddingStore")
	public EmbeddingStore<TextSegment> cacheEmbeddingStore(
			@Value("${app.pgvector.host}") String host,
			@Value("${app.pgvector.port}") int port,
			@Value("${app.pgvector.database}") String database,
			@Value("${app.pgvector.user}") String user,
			@Value("${app.pgvector.password}") String password,
			@Value("${app.cache.table}") String table,
			@Value("${app.pgvector.dimension}") int dimension) {
		log.info("Initializing semantic cache embedding store, table={}", table);
		return PgVectorEmbeddingStore.builder()
				.host(host)
				.port(port)
				.database(database)
				.user(user)
				.password(password)
				.table(table)
				.dimension(dimension)
				.createTable(true)
				.build();
	}
}
