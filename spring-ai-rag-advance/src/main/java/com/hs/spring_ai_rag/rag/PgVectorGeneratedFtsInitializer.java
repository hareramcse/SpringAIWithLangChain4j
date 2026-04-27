package com.hs.spring_ai_rag.rag;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.hs.spring_ai_rag.config.AppPgVectorProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adds a STORED {@code tsvector} generated column and GIN index for hybrid retrieval. Runs after the
 * LangChain4j pgvector table exists but before {@link com.hs.spring_ai_rag.bootstrap.IngestionRunner} loads data.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@DependsOn("embeddingStore")
@RequiredArgsConstructor
public class PgVectorGeneratedFtsInitializer implements ApplicationRunner {

	private final AppPgVectorProperties pg;
	private final JdbcClient jdbc;

	@Override
	public void run(ApplicationArguments args) {
		if (!pg.hybridRetrieval()) {
			log.info("Skipping pgvector FTS DDL (app.pgvector.hybrid-retrieval=false).");
			return;
		}
		PgVectorSqlIdentifiers.requireSqlIdentifier("table", pg.table());
		PgVectorSqlIdentifiers.requireSqlIdentifier("tsv-column", pg.tsvColumn());
		PgVectorSqlIdentifiers.requireRegconfig(pg.textSearchConfig());

		String addColumn = "ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s tsvector "
				+ "GENERATED ALWAYS AS (to_tsvector('%s', coalesce(text, ''))) STORED"
				.formatted(pg.table(), pg.tsvColumn(), pg.textSearchConfig());
		jdbc.sql(addColumn).update();

		String indexName = pg.table() + "_" + pg.tsvColumn() + "_gin_idx";
		PgVectorSqlIdentifiers.requireSqlIdentifier("fts-index", indexName);
		String createIndex =
				"CREATE INDEX IF NOT EXISTS %s ON %s USING gin (%s)".formatted(indexName, pg.table(), pg.tsvColumn());
		jdbc.sql(createIndex).update();
		log.info(
				"Ensured FTS column {}.{} (regconfig {}) and index {}.",
				pg.table(),
				pg.tsvColumn(),
				pg.textSearchConfig(),
				indexName);
	}
}
