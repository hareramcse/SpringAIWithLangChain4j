package com.hs.spring_ai_research.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the PGVector embedding store and content retriever for the knowledge base.
 *
 * <p>The embedding store holds document chunks as vectors in PostgreSQL+pgvector.
 * The content retriever is used by the RAG pipeline to search for relevant chunks.</p>
 */
@Slf4j
@Configuration
public class RagConfig {

	/**
	 * Knowledge base vector store — all ingested document chunks live here.
	 * Table is auto-created on first startup.
	 */
	@Bean("researchEmbeddingStore")
	public EmbeddingStore<TextSegment> researchEmbeddingStore(
			@Value("${app.pgvector.host}") String host,
			@Value("${app.pgvector.port}") int port,
			@Value("${app.pgvector.database}") String database,
			@Value("${app.pgvector.user}") String user,
			@Value("${app.pgvector.password}") String password,
			@Value("${app.pgvector.table}") String table,
			@Value("${app.pgvector.dimension}") int dimension) {
		log.info("Initializing PGVector embedding store: {}:{}/{} table={}", host, port, database, table);
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

	/**
	 * Default content retriever with minScore=0.5 and maxResults=10.
	 * The {@link com.hs.spring_ai_research.rag.AdvancedRagService} builds on top
	 * of this with hybrid retrieval, re-ranking, and compression.
	 */
	@Bean
	public ContentRetriever contentRetriever(
			EmbeddingStore<TextSegment> researchEmbeddingStore,
			EmbeddingModel embeddingModel) {
		return EmbeddingStoreContentRetriever.builder()
				.embeddingStore(researchEmbeddingStore)
				.embeddingModel(embeddingModel)
				.maxResults(10)
				.minScore(0.5)
				.build();
	}
}
