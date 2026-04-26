package com.hs.spring_ai_rag.rag;

import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;

/**
 * Re-ranks fused retrieval with a {@link ScoringModel}, then optionally applies {@link MmrSelector}
 * so the answer model does not receive near-duplicate chunks.
 * <p>
 * With {@link dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer}, scores must use the
 * <b>original</b> user message — see {@link #originalUserQuery()}.
 */
public class SimpleRerankingAggregator extends ReRankingContentAggregator {

	private final int finalMaxResults;
	private final boolean mmrEnabled;
	private final double mmrLambda;
	private final EmbeddingModel embeddingModel;

	public SimpleRerankingAggregator(
			ScoringModel crossEncoder,
			Double minRerankScore,
			int rerankCandidateCap,
			int finalMaxResults,
			boolean mmrEnabled,
			double mmrLambda,
			EmbeddingModel embeddingModel) {
		super(crossEncoder, originalUserQuery(), minRerankScore, rerankCandidateCap);
		this.finalMaxResults = finalMaxResults;
		this.mmrEnabled = mmrEnabled;
		this.mmrLambda = mmrLambda;
		this.embeddingModel = embeddingModel;
	}

	/**
	 * Resolves the real user question for scoring (metadata is copied onto expanded queries by LangChain4j).
	 */
	public static Function<Map<Query, Collection<List<Content>>>, Query> originalUserQuery() {
		return queryToContents -> {
			Query any = queryToContents.keySet().iterator().next();
			Metadata meta = any.metadata();
			if (meta != null) {
				ChatMessage cm = meta.chatMessage();
				if (cm instanceof UserMessage userMessage) {
					return Query.from(userMessage.singleText(), meta);
				}
			}
			return any;
		};
	}

	@Override
	public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
		if (queryToContents.isEmpty()) {
			return emptyList();
		}
		Query query = originalUserQuery().apply(queryToContents);
		List<Content> fused = ReciprocalRankFuser.fuse(fuse(queryToContents).values());
		if (fused.isEmpty()) {
			return fused;
		}
		List<Content> reranked = reRankAndFilter(fused, query);
		return MmrSelector.pickFinal(reranked, finalMaxResults, mmrEnabled, mmrLambda, embeddingModel);
	}
}
