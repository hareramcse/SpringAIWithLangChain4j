package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.pgvector.*} from {@code application.yaml}.
 */
@ConfigurationProperties(prefix = "app.pgvector")
public record AppPgVectorProperties(
		String host,
		int port,
		String database,
		String user,
		String password,
		String table,
		int dimension) {
}
