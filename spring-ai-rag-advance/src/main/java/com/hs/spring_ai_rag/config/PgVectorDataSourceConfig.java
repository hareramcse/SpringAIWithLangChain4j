package com.hs.spring_ai_rag.config;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * JDBC access for hybrid FTS on the same database as {@link dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore}.
 */
@Configuration
public class PgVectorDataSourceConfig {

	@Bean
	public DataSource pgVectorDataSource(AppPgVectorProperties pg) {
		var ds = new PGSimpleDataSource();
		ds.setServerNames(new String[] {pg.host()});
		ds.setPortNumbers(new int[] {pg.port()});
		ds.setDatabaseName(pg.database());
		ds.setUser(pg.user());
		ds.setPassword(pg.password());
		return ds;
	}

	@Bean
	public JdbcClient pgVectorJdbcClient(DataSource pgVectorDataSource) {
		return JdbcClient.create(pgVectorDataSource);
	}
}
