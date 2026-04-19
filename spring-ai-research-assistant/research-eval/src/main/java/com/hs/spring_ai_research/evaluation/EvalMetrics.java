package com.hs.spring_ai_research.evaluation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Extended evaluation metrics beyond basic LLM-as-judge.
 *
 * Metrics computed:
 * 1. Correctness — semantic similarity between generated answer and gold answer
 * 2. Retrieval Precision — what fraction of retrieved chunks are actually relevant
 * 3. Retrieval Recall — what fraction of relevant info was retrieved
 * 4. Answer Completeness — does the answer cover all key points from the expected answer
 *
 * These metrics, combined with the existing faithfulness/relevance/hallucination scores,
 * give a comprehensive picture of pipeline quality.
 */
@Slf4j
@Service
public class EvalMetrics {

	private final EmbeddingModel embeddingModel;
	private final ChatModel fastModel;

	public EvalMetrics(
			EmbeddingModel embeddingModel,
			@Qualifier("fastModel") ChatModel fastModel) {
		this.embeddingModel = embeddingModel;
		this.fastModel = fastModel;
	}

	/**
	 * Correctness: cosine similarity between generated answer and gold answer embeddings.
	 * Returns a score between 0.0 and 1.0.
	 */
	public double scoreCorrectness(String generatedAnswer, String expectedAnswer) {
		Embedding genEmb = embeddingModel.embed(generatedAnswer).content();
		Embedding expEmb = embeddingModel.embed(expectedAnswer).content();
		double similarity = cosineSimilarity(genEmb.vector(), expEmb.vector());
		log.debug("Correctness score (semantic similarity): {}", String.format("%.3f", similarity));
		return Math.max(0.0, similarity);
	}

	/**
	 * Retrieval Precision: what fraction of retrieved segments are relevant
	 * to the gold context. Uses keyword overlap as a fast heuristic.
	 */
	public double scoreRetrievalPrecision(List<TextSegment> retrieved, String goldContext) {
		if (retrieved.isEmpty()) return 0.0;

		Set<String> goldKeywords = extractKeywords(goldContext);
		long relevant = retrieved.stream()
				.filter(seg -> hasSignificantOverlap(extractKeywords(seg.text()), goldKeywords))
				.count();

		double precision = (double) relevant / retrieved.size();
		log.debug("Retrieval precision: {}/{} = {}", relevant, retrieved.size(), String.format("%.3f", precision));
		return precision;
	}

	/**
	 * Retrieval Recall: what fraction of the gold context's key concepts
	 * appear in at least one retrieved segment.
	 */
	public double scoreRetrievalRecall(List<TextSegment> retrieved, String goldContext) {
		Set<String> goldKeywords = extractKeywords(goldContext);
		if (goldKeywords.isEmpty()) return 1.0;

		Set<String> retrievedKeywords = new HashSet<>();
		for (TextSegment seg : retrieved) {
			retrievedKeywords.addAll(extractKeywords(seg.text()));
		}

		long found = goldKeywords.stream()
				.filter(retrievedKeywords::contains)
				.count();

		double recall = (double) found / goldKeywords.size();
		log.debug("Retrieval recall: {}/{} = {}", found, goldKeywords.size(), String.format("%.3f", recall));
		return recall;
	}

	/**
	 * Answer Completeness: uses fast LLM to check if the generated answer
	 * covers all key points from the expected answer. Returns 1-10 score.
	 */
	public int scoreCompleteness(String generatedAnswer, String expectedAnswer) {
		String prompt = """
				Compare the generated answer with the expected answer.
				Score how completely the generated answer covers the key points.
				
				Expected answer (gold standard):
				%s
				
				Generated answer:
				%s
				
				Score from 1-10 where:
				- 10: All key points covered with equal or better detail
				- 7-9: Most key points covered, minor gaps
				- 4-6: Several key points missing
				- 1-3: Significantly incomplete
				
				Respond with ONLY a single integer (1-10):""".formatted(
				truncate(expectedAnswer, 1000), truncate(generatedAnswer, 1000));

		try {
			String response = fastModel.chat(prompt).trim().replaceAll("[^0-9]", "");
			int score = response.isEmpty() ? 5 : Integer.parseInt(response.substring(0, Math.min(2, response.length())));
			return Math.max(1, Math.min(10, score));
		} catch (Exception e) {
			log.warn("Completeness scoring failed, defaulting to 5: {}", e.getMessage());
			return 5;
		}
	}

	public record MetricsResult(
			double correctness,
			double retrievalPrecision,
			double retrievalRecall,
			int completeness,
			double compositeScore
	) {}

	public MetricsResult computeAll(String generatedAnswer, String expectedAnswer,
									List<TextSegment> retrieved, String goldContext) {
		double correctness = scoreCorrectness(generatedAnswer, expectedAnswer);
		double precision = scoreRetrievalPrecision(retrieved, goldContext);
		double recall = scoreRetrievalRecall(retrieved, goldContext);
		int completeness = scoreCompleteness(generatedAnswer, expectedAnswer);

		double composite = (correctness * 0.3) + (precision * 0.2) + (recall * 0.2) + (completeness / 10.0 * 0.3);
		return new MetricsResult(correctness, precision, recall, completeness, composite);
	}

	private Set<String> extractKeywords(String text) {
		Set<String> stopWords = Set.of("the", "a", "an", "is", "are", "was", "were", "in", "on",
				"at", "to", "for", "of", "with", "by", "from", "and", "or", "not", "it", "this", "that");
		Set<String> keywords = new HashSet<>();
		for (String word : text.toLowerCase().split("\\W+")) {
			if (word.length() > 2 && !stopWords.contains(word)) {
				keywords.add(word);
			}
		}
		return keywords;
	}

	private boolean hasSignificantOverlap(Set<String> segKeywords, Set<String> goldKeywords) {
		long overlap = segKeywords.stream().filter(goldKeywords::contains).count();
		return overlap >= Math.max(2, goldKeywords.size() * 0.2);
	}

	private double cosineSimilarity(float[] a, float[] b) {
		double dotProduct = 0, normA = 0, normB = 0;
		for (int i = 0; i < a.length; i++) {
			dotProduct += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		double denominator = Math.sqrt(normA) * Math.sqrt(normB);
		return denominator == 0 ? 0 : dotProduct / denominator;
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}
}
