package com.hs.spring_ai_rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.hs.spring_ai_rag.rag.LlmCrossEncoderScoringModel;
import com.hs.spring_ai_rag.rag.SimpleRerankingAggregator;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

/**
 * AI / RAG wiring kept small for a POC: pgvector retrieval, optional query expansion, then optional re-rank + MMR
 * (see {@link SimpleRerankingAggregator} and {@link LlmCrossEncoderScoringModel}).
 */
@Configuration
public class AiConfig {

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

	/** LLM scores each passage vs the query (cross-encoder style for a POC). */
	@Bean
	public ScoringModel crossEncoderScoringModel(ChatModel chatModel, Environment environment) {
		int batch = environment.getProperty("app.rag.reranking.cross-encoder-max-segments-per-call", Integer.class, 6);
		return new LlmCrossEncoderScoringModel(chatModel, batch);
	}

	@Bean
	public ContentRetriever contentRetriever(
			EmbeddingStore<TextSegment> embeddingStore,
			EmbeddingModel embeddingModel,
			Environment environment,
			@Value("${app.rag.retrieval.min-score}") double minScore,
			@Value("${app.rag.retrieval.max-results}") int maxResults,
			@Value("${app.rag.retrieval.initial-max-results}") int initialMaxResults) {
		boolean rerankOn = environment.getProperty("app.rag.reranking.enabled", Boolean.class, false);
		validatePool(environment, rerankOn);
		int pool = rerankOn ? initialMaxResults : maxResults;
		return EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.maxResults(pool)
				.minScore(minScore)
				.build();
	}

	@Bean
	public RetrievalAugmentor retrievalAugmentor(
			ContentRetriever contentRetriever,
			ChatModel chatModel,
			Environment environment,
			EmbeddingModel embeddingModel,
			ScoringModel crossEncoderScoringModel,
			@Value("${app.rag.reranking.enabled}") boolean rerankingEnabled,
			@Value("${app.rag.retrieval.max-results}") int finalMaxResults,
			@Value("${app.rag.reranking.rerank-candidate-cap}") int rerankCandidateCap,
			@Value("${app.rag.reranking.mmr-enabled}") boolean mmrEnabled,
			@Value("${app.rag.reranking.mmr-lambda}") double mmrLambda) {
		Double minRerank = environment.getProperty("app.rag.reranking.min-score", Double.class);
		ContentAggregator aggregator = rerankingEnabled
				? new SimpleRerankingAggregator(
						crossEncoderScoringModel,
						minRerank,
						rerankCandidateCap,
						finalMaxResults,
						mmrEnabled,
						clamp01(mmrLambda),
						embeddingModel)
				: new DefaultContentAggregator();
		return DefaultRetrievalAugmentor.builder()
				.contentRetriever(contentRetriever)
				.queryTransformer(new ExpandingQueryTransformer(chatModel, 3))
				.contentAggregator(aggregator)
				.build();
	}

	private static void validatePool(Environment env, boolean rerankOn) {
		int max = env.getProperty("app.rag.retrieval.max-results", Integer.class, 0);
		int initial = env.getProperty("app.rag.retrieval.initial-max-results", Integer.class, 0);
		if (max <= 0 || initial <= 0) {
			throw new IllegalStateException("app.rag.retrieval.max-results and initial-max-results must be positive");
		}
		if (!rerankOn) {
			return;
		}
		int cap = env.getProperty("app.rag.reranking.rerank-candidate-cap", Integer.class, 0);
		if (cap <= 0 || cap < max || initial < cap) {
			throw new IllegalStateException(
					"When reranking is on: rerank-candidate-cap >= max-results and initial-max-results >= rerank-candidate-cap");
		}
	}

	private static double clamp01(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}
}
