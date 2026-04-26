package com.hs.spring_ai_rag.rag;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;

/**
 * Picks the final {@link Content} list after re-ranking: either plain top-k or MMR for diversity.
 */
public final class MmrSelector {

	private MmrSelector() {
	}

	public static List<Content> pickFinal(
			List<Content> reranked,
			int maxResults,
			boolean mmrEnabled,
			double mmrLambda,
			EmbeddingModel embeddingModel) {
		if (reranked.isEmpty() || maxResults <= 0) {
			return List.of();
		}
		if (!mmrEnabled) {
			return reranked.stream().limit(maxResults).toList();
		}
		return selectByMmr(reranked, maxResults, mmrLambda, embeddingModel);
	}

	private static List<Content> selectByMmr(
			List<Content> ranked,
			int k,
			double lambda,
			EmbeddingModel model) {
		if (ranked.size() <= k) {
			return List.copyOf(ranked);
		}
		List<TextSegment> segments = ranked.stream().map(Content::textSegment).toList();
		List<Embedding> vectors = model.embedAll(segments).content();

		List<Content> pool = new ArrayList<>(ranked);
		List<Integer> poolIdx = indicesFromSize(ranked.size());
		List<Content> picked = new ArrayList<>();
		List<Integer> pickedIdx = new ArrayList<>();

		while (picked.size() < k && !pool.isEmpty()) {
			int bestPos = indexOfBestMmrCandidate(pool, poolIdx, vectors, pickedIdx, lambda);
			if (bestPos < 0) {
				break;
			}
			pickedIdx.add(poolIdx.remove(bestPos));
			picked.add(pool.remove(bestPos));
		}
		return picked;
	}

	private static List<Integer> indicesFromSize(int n) {
		List<Integer> idx = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			idx.add(i);
		}
		return idx;
	}

	private static int indexOfBestMmrCandidate(
			List<Content> pool,
			List<Integer> poolIdx,
			List<Embedding> vectors,
			List<Integer> pickedIdx,
			double lambda) {
		int bestPos = -1;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int p = 0; p < pool.size(); p++) {
			int globalIdx = poolIdx.get(p);
			double relevance = relevanceScore(pool.get(p));
			double redundancy = maxCosineToChosen(vectors.get(globalIdx), pickedIdx, vectors);
			double mmr = lambda * relevance - (1.0 - lambda) * redundancy;
			if (mmr > bestScore) {
				bestScore = mmr;
				bestPos = p;
			}
		}
		return bestPos;
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
		double dot = 0;
		double na = 0;
		double nb = 0;
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
