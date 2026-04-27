package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.pgvector.*} from {@code application.yaml}. Defaults for hybrid FTS live in YAML.
 */
@ConfigurationProperties(prefix = "app.pgvector")
public record AppPgVectorProperties(
		String host,
		int port,
		String database,
		String user,
		String password,
		String table,
		int dimension,
		/** PostgreSQL text search configuration name (e.g. {@code simple}, {@code english}). */
		String textSearchConfig,
		/** STORED generated column holding {@code to_tsvector(text_search_config, coalesce(text,''))}. */
		String tsvColumn,
		/** When true, retrieval runs dense pgvector search and FTS on {@link #tsvColumn()}, fused with RRF. */
		boolean hybridRetrieval,
		/** RRF {@code k} when merging dense and keyword ranked lists. */
		int rrfK) {
}
