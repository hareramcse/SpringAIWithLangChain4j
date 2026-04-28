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
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Embedding store, dense/hybrid retriever, optional LLM re-ranking of retrieved chunks, guardrail.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({ AppRagProperties.class, AppPgVectorProperties.class, AppChatSafetyProperties.class })
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
	public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever, ChatModel chatModel,
			ScoringModel passageRelevanceScoringModel, AppRagProperties rag) {
		var reranking = rag.reranking();
		ContentAggregator aggregator = reranking.enabled()
				? ReRankingContentAggregator.builder().scoringModel(passageRelevanceScoringModel)
						.querySelector(RagQuerySelectors.rerankingQuerySelector()).minScore(reranking.minScore())
						.maxResults(rag.retrieval().maxResults()).build()
				: new DefaultContentAggregator();
		var augmentor = DefaultRetrievalAugmentor.builder().contentRetriever(contentRetriever)
				.contentAggregator(aggregator).build();
		return new GatingRetrievalAugmentor(augmentor, rag);
	}

	@Bean
	public DocumentSplitter documentSplitter(AppRagProperties rag) {
		var c = rag.chunking();
		var splitter = DocumentSplitters.recursive(c.maxSegmentSizeChars(), c.maxOverlapChars());
		log.info("Chunking: recursive, maxSegmentSizeChars={}, maxOverlapChars={}", c.maxSegmentSizeChars(),
				c.maxOverlapChars());
		return splitter;
	}

	private void validateRetrievalConfiguration(AppRagProperties rag) {
		var retrievalSettings = rag.retrieval();
		if (retrievalSettings.maxResults() <= 0 || retrievalSettings.initialMaxResults() <= 0) {
			throw new IllegalStateException("app.rag.retrieval.max-results and initial-max-results must be positive");
		}
		if (!rag.reranking().enabled()) {
			return;
		}
		if (retrievalSettings.initialMaxResults() < retrievalSettings.maxResults()) {
			throw new IllegalStateException(
					"When reranking is enabled: app.rag.retrieval.initial-max-results must be >= app.rag.retrieval.max-results "
							+ "(retrieve a candidate pool, then re-rank down to max-results).");
		}
	}
}
