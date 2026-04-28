package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code app.pgvector.*} from {@code application.yaml}. Uses JavaBean binding so fields map reliably
 * to YAML keys (record constructor binding can mis-map when parameter names are not preserved in bytecode).
 */
@ConfigurationProperties(prefix = "app.pgvector")
@Getter
@Setter
public class AppPgVectorProperties {

	private String host;
	private int port;
	private String database;
	private String user;
	private String password;
	private String table;
	private int dimension;
	/** PostgreSQL text search configuration name (e.g. {@code simple}, {@code english}) — not the table name. */
	private String textSearchConfig = "simple";
	/** STORED generated column for {@code to_tsvector(text_search_config, coalesce(text,''))}. */
	private String tsvColumn = "text_tsv";
	/** When true: run dense pgvector and FTS, merge distinct chunks, then downstream re-ranking (if enabled). */
	private boolean hybridRetrieval = true;
	/** Max dense hits per request before merging with FTS (each leg runs independently). */
	private int hybridVectorMaxResults = 5;
	/** Max FTS hits per request before merging with dense hits. */
	private int hybridKeywordMaxResults = 5;
}
