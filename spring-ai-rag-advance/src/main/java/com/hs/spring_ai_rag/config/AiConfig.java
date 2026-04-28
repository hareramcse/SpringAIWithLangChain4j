package com.hs.spring_ai_rag.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.spring_ai_rag.rag.GatingRetrievalAugmentor;
import com.hs.spring_ai_rag.rag.HybridPgVectorContentRetriever;
import com.hs.spring_ai_rag.rag.LlmCrossEncoderScoringModel;
import com.hs.spring_ai_rag.rag.RagQuerySelectors;
import com.hs.spring_ai_rag.rag.injector.CompressingContentInjector;
import com.hs.spring_ai_rag.rag.query.NormalizingQueryTransformer;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

/**
 * Embedding store, retrieval pipeline (query transform → hybrid search → re-rank → context compression → inject), guardrail.
 */
@Configuration
@EnableConfigurationProperties({ AppRagProperties.class, AppPgVectorProperties.class, AppChatSafetyProperties.class,
		AppRagPipelineProperties.class })
public class AiConfig {

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	public EmbeddingStore<TextSegment> embeddingStore(AppPgVectorProperties pg) {
		return PgVectorEmbeddingStore.builder().host(pg.getHost()).port(pg.getPort()).database(pg.getDatabase())
				.user(pg.getUser()).password(pg.getPassword()).table(pg.getTable()).dimension(pg.getDimension())
				.createTable(false).build();
	}

	@Bean
	public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel,
			JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AppPgVectorProperties pg, AppRagProperties rag) {
		validateRetrievalConfiguration(rag);
		var retrievalSettings = rag.retrieval();
		int maxChunksToRetrieve = rag.reranking().enabled() ? retrievalSettings.initialMaxResults()
				: retrievalSettings.maxResults();
		if (pg.isHybridRetrieval()) {
			return new HybridPgVectorContentRetriever(embeddingStore, embeddingModel, jdbcTemplate, objectMapper, pg,
					rag, maxChunksToRetrieve);
		}
		return EmbeddingStoreContentRetriever.builder().embeddingStore(embeddingStore).embeddingModel(embeddingModel)
				.maxResults(maxChunksToRetrieve).minScore(retrievalSettings.minScore()).build();
	}

	@Bean
	public ScoringModel passageRelevanceScoringModel(ChatModel chatModel, AppRagProperties rag) {
		return new LlmCrossEncoderScoringModel(chatModel, rag.reranking().crossEncoderMaxSegmentsPerCall());
	}

	@Bean
	public QueryTransformer queryTransformer(AppRagPipelineProperties pipeline) {
		if (pipeline.isQueryNormalizationEnabled()) {
			return new NormalizingQueryTransformer();
		}
		return new DefaultQueryTransformer();
	}

	@Bean
	public ContentInjector ragContentInjector(AppRagPipelineProperties pipeline) {
		ContentInjector inner = new DefaultContentInjector();
		if (pipeline.isContextCompressionEnabled()) {
			return new CompressingContentInjector(inner, pipeline.getMaxInjectedContextChars());
		}
		return inner;
	}

	@Bean
	public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever, ChatModel chatModel,
			ScoringModel passageRelevanceScoringModel, AppRagProperties rag, QueryTransformer queryTransformer,
			ContentInjector ragContentInjector) {
		var reranking = rag.reranking();
		ContentAggregator aggregator = reranking.enabled()
				? ReRankingContentAggregator.builder().scoringModel(passageRelevanceScoringModel)
						.querySelector(RagQuerySelectors.rerankingQuerySelector()).minScore(reranking.minScore())
						.maxResults(rag.retrieval().maxResults()).build()
				: new DefaultContentAggregator();
		var augmentor = DefaultRetrievalAugmentor.builder().queryTransformer(queryTransformer)
				.contentRetriever(contentRetriever).contentAggregator(aggregator).contentInjector(ragContentInjector)
				.build();
		return new GatingRetrievalAugmentor(augmentor, rag);
	}

	@Bean
	public DocumentSplitter documentSplitter(AppRagProperties rag) {
		var c = rag.chunking();
		return DocumentSplitters.recursive(c.maxSegmentSizeChars(), c.maxOverlapChars());
	}

	private void validateRetrievalConfiguration(AppRagProperties rag) {
		var retrievalSettings = rag.retrieval();
		if (retrievalSettings.maxResults() <= 0 || retrievalSettings.initialMaxResults() <= 0) {
			throw new IllegalStateException("invalid retrieval limits");
		}
		if (!rag.reranking().enabled()) {
			return;
		}
		if (retrievalSettings.initialMaxResults() < retrievalSettings.maxResults()) {
			throw new IllegalStateException("initial-max-results must be >= max-results when reranking");
		}
	}
}
