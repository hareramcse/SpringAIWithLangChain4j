package com.hs.spring_ai_help_desk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

@Configuration
public class RagConfig {

	@Bean
	public EmbeddingStore<TextSegment> embeddingStore(
			@Value("${app.pgvector.host}") String host,
			@Value("${app.pgvector.port}") int port,
			@Value("${app.pgvector.database}") String database,
			@Value("${app.pgvector.user}") String user,
			@Value("${app.pgvector.password}") String password,
			@Value("${app.pgvector.table}") String table,
			@Value("${app.pgvector.dimension}") int dimension) {
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

	@Bean
	public ContentRetriever contentRetriever(
			EmbeddingStore<TextSegment> embeddingStore,
			EmbeddingModel embeddingModel) {
		return EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.maxResults(5)
				.minScore(0.5)
				.build();
	}

}
