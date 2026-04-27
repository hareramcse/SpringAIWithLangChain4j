package com.hs.spring_ai_rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.rag.GatingRetrievalAugmentor;
import com.hs.spring_ai_rag.rag.HybridPgVectorContentRetriever;
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
 * Central RAG wiring: embedding store, dense/hybrid retriever, query expansion, re-rank + MMR, confidence guardrail.
 */
@Configuration
@EnableConfigurationProperties({AppRagProperties.class, AppPgVectorProperties.class, AppEvalProperties.class})
public class AiConfig {

	/** Paraphrases from {@link ExpandingQueryTransformer}; not bound from YAML in this project. */
	private static final int QUERY_EXPANSION_VARIANTS = 3;

	// --- Storage ---

	@Bean
	public EmbeddingStore<TextSegment> embeddingStore(AppPgVectorProperties pg) {
		return PgVectorEmbeddingStore.builder()
				.host(pg.host())
				.port(pg.port())
				.database(pg.database())
				.user(pg.user())
				.password(pg.password())
				.table(pg.table())
				.dimension(pg.dimension())
				.createTable(true)
				.build();
	}

	// --- Candidate retrieval (dense vector and optional hybrid FTS) ---

	@Bean
	public ContentRetriever contentRetriever(
			EmbeddingStore<TextSegment> embeddingStore,
			EmbeddingModel embeddingModel,
			JdbcClient pgVectorJdbcClient,
			ObjectMapper objectMapper,
			AppPgVectorProperties pg,
			AppRagProperties rag) {
		assertRetrievalPoolValid(rag);
		var retrieval = rag.retrieval();
		int pool = rag.reranking().enabled() ? retrieval.initialMaxResults() : retrieval.maxResults();
		if (pg.hybridRetrieval()) {
			return new HybridPgVectorContentRetriever(
					embeddingStore, embeddingModel, pgVectorJdbcClient, objectMapper, pg, rag, pool);
		}
		return EmbeddingStoreContentRetriever.builder()
				.embeddingStore(embeddingStore)
				.embeddingModel(embeddingModel)
				.maxResults(pool)
				.minScore(retrieval.minScore())
				.build();
	}

	// --- Re-ranking model (LLM scores passages) ---

	@Bean
	public ScoringModel crossEncoderScoringModel(ChatModel chatModel, AppRagProperties rag) {
		return new LlmCrossEncoderScoringModel(chatModel, rag.reranking().crossEncoderMaxSegmentsPerCall());
	}

	// --- Retrieval augmentor (expand → retrieve → fuse → re-rank/MMR → guardrail) ---

	@Bean
	public RetrievalAugmentor retrievalAugmentor(
			ContentRetriever contentRetriever,
			ChatModel chatModel,
			EmbeddingModel embeddingModel,
			ScoringModel crossEncoderScoringModel,
			AppRagProperties rag) {
		var reranking = rag.reranking();
		ContentAggregator aggregator = reranking.enabled()
				? new SimpleRerankingAggregator(
						crossEncoderScoringModel,
						reranking.minScore(),
						reranking.rerankCandidateCap(),
						rag.retrieval().maxResults(),
						reranking.mmrEnabled(),
						clampToUnitInterval(reranking.mmrLambda()),
						embeddingModel)
				: new DefaultContentAggregator();
		var augmentor = DefaultRetrievalAugmentor.builder()
				.contentRetriever(contentRetriever)
				.queryTransformer(new ExpandingQueryTransformer(chatModel, QUERY_EXPANSION_VARIANTS))
				.contentAggregator(aggregator)
				.build();
		return new GatingRetrievalAugmentor(augmentor, rag);
	}

	private static void assertRetrievalPoolValid(AppRagProperties rag) {
		var retrieval = rag.retrieval();
		if (retrieval.maxResults() <= 0 || retrieval.initialMaxResults() <= 0) {
			throw new IllegalStateException("app.rag.retrieval.max-results and initial-max-results must be positive");
		}
		if (!rag.reranking().enabled()) {
			return;
		}
		var reranking = rag.reranking();
		int cap = reranking.rerankCandidateCap();
		if (cap <= 0 || cap < retrieval.maxResults() || retrieval.initialMaxResults() < cap) {
			throw new IllegalStateException(
					"When reranking is enabled: app.rag.reranking.rerank-candidate-cap must be "
							+ ">= app.rag.retrieval.max-results and <= app.rag.retrieval.initial-max-results");
		}
	}

	private static double clampToUnitInterval(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}
}
