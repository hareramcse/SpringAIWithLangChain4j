package com.hs.spring_ai_rag.rag;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;

/**
 * POC-friendly re-ranking in one place:
 * <ol>
 * <li>Uses LangChain4j's {@link ReRankingContentAggregator} (via {@code super}) to score passages with a {@link ScoringModel} — here the LLM "cross-encoder".</li>
 * <li>Optionally applies <b>MMR</b> (maximal marginal relevance) so the final context is not full of near-duplicate chunks.</li>
 * </ol>
 * When the app uses {@link dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer}, we must score against the
 * <b>original user message</b>, not a random expanded line — see {@link #originalUserQuery()}.
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
	 * Pick the real user question for scoring (metadata is copied onto expanded queries by LangChain4j).
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
		Map<Query, List<Content>> perQuery = fuse(queryToContents);
		List<Content> fused = ReciprocalRankFuser.fuse(perQuery.values());
		if (fused.isEmpty()) {
			return fused;
		}
		List<Content> reranked = reRankAndFilter(fused, query);
		if (mmrEnabled) {
			return mmrSelect(reranked, finalMaxResults, mmrLambda, embeddingModel);
		}
		return reranked.stream().limit(finalMaxResults).toList();
	}

	// --- MMR (kept here so beginners see the full POC flow in one file) ---

	private static List<Content> mmrSelect(List<Content> ranked, int k, double lambda, EmbeddingModel model) {
		if (ranked.isEmpty() || k <= 0) {
			return List.of();
		}
		if (ranked.size() <= k) {
			return List.copyOf(ranked);
		}
		List<TextSegment> segments = ranked.stream().map(Content::textSegment).toList();
		List<Embedding> vectors = model.embedAll(segments).content();

		List<Content> pool = new ArrayList<>(ranked);
		List<Integer> poolIdx = new ArrayList<>();
		for (int i = 0; i < ranked.size(); i++) {
			poolIdx.add(i);
		}
		List<Content> picked = new ArrayList<>();
		List<Integer> pickedIdx = new ArrayList<>();

		while (picked.size() < k && !pool.isEmpty()) {
			int bestPos = -1;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (int p = 0; p < pool.size(); p++) {
				int g = poolIdx.get(p);
				double relevance = relevanceScore(pool.get(p));
				double redundancy = maxCosineToChosen(vectors.get(g), pickedIdx, vectors);
				double mmr = lambda * relevance - (1.0 - lambda) * redundancy;
				if (mmr > bestScore) {
					bestScore = mmr;
					bestPos = p;
				}
			}
			if (bestPos < 0) {
				break;
			}
			pickedIdx.add(poolIdx.remove(bestPos));
			picked.add(pool.remove(bestPos));
		}
		return picked;
	}

	private static double relevanceScore(Content c) {
		Object r = c.metadata().get(ContentMetadata.RERANKED_SCORE);
		if (r instanceof Number n) {
			return n.doubleValue();
		}
		Object s = c.metadata().get(ContentMetadata.SCORE);
		if (s instanceof Number n) {
			return n.doubleValue();
		}
		return 0.0;
	}

	private static double maxCosineToChosen(Embedding candidate, List<Integer> chosen, List<Embedding> all) {
		if (chosen.isEmpty()) {
			return 0.0;
		}
		float[] v = candidate.vector();
		double max = 0.0;
		for (int idx : chosen) {
			max = Math.max(max, cosine(v, all.get(idx).vector()));
		}
		return max;
	}

	private static double cosine(float[] a, float[] b) {
		double dot = 0, na = 0, nb = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		if (na == 0 || nb == 0) {
			return 0.0;
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}
}
