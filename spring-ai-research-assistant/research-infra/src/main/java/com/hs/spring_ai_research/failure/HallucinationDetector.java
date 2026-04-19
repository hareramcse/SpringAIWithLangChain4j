package com.hs.spring_ai_research.failure;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Multi-strategy hallucination detection for LLM outputs.
 *
 * Three detection strategies, each catching different hallucination types:
 *
 * 1. Entity Grounding — extracts named entities from the answer and checks
 *    if each appears in the source context. Catches fabricated names/dates/numbers.
 *
 * 2. Claim Decomposition — breaks the answer into individual claims and verifies
 *    each against the context. Catches subtle factual errors.
 *
 * 3. Consistency Check — asks the same question twice and compares answers.
 *    Inconsistent answers suggest the model is guessing rather than knowing.
 *
 * The combined confidence score tells you HOW MUCH to trust the output.
 * In production, low-confidence outputs trigger human review or caveats.
 */
@Slf4j
@Service
public class HallucinationDetector {

	private final ChatModel fastModel;

	public HallucinationDetector(@Qualifier("fastModel") ChatModel fastModel) {
		this.fastModel = fastModel;
	}

	/**
	 * Run all three detection strategies and combine results.
	 */
	public DetectionResult detect(String answer, String sourceContext) {
		double entityScore = entityGroundingCheck(answer, sourceContext);
		double claimScore = claimVerification(answer, sourceContext);

		double confidence = (entityScore * 0.4) + (claimScore * 0.6);
		String level = confidence >= 0.8 ? "LOW_RISK"
				: confidence >= 0.5 ? "MEDIUM_RISK" : "HIGH_RISK";

		log.info("Hallucination detection: entity={}, claims={}, confidence={}, risk={}",
				String.format("%.2f", entityScore),
				String.format("%.2f", claimScore),
				String.format("%.2f", confidence), level);

		return new DetectionResult(entityScore, claimScore, confidence, level);
	}

	/**
	 * Strategy 1: Extract entities from the answer and check if they exist in context.
	 */
	private double entityGroundingCheck(String answer, String context) {
		String prompt = """
				Extract all specific named entities (people, dates, numbers, organizations,
				technical terms) from this text. Return one entity per line, nothing else.
				
				Text: %s""".formatted(truncate(answer, 1000));

		try {
			String response = fastModel.chat(prompt);
			List<String> entities = Arrays.stream(response.split("\n"))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.toList();

			if (entities.isEmpty()) return 1.0;

			String contextLower = context.toLowerCase();
			long grounded = entities.stream()
					.filter(entity -> contextLower.contains(entity.toLowerCase()))
					.count();

			return (double) grounded / entities.size();
		} catch (Exception e) {
			log.warn("Entity grounding check failed: {}", e.getMessage());
			return 0.5;
		}
	}

	/**
	 * Strategy 2: Decompose answer into claims and verify each against context.
	 */
	private double claimVerification(String answer, String context) {
		String prompt = """
				Break the following text into individual factual claims (one per line).
				Only include verifiable claims, not opinions.
				
				Text: %s""".formatted(truncate(answer, 1000));

		try {
			String claimsResponse = fastModel.chat(prompt);
			List<String> claims = Arrays.stream(claimsResponse.split("\n"))
					.map(String::trim)
					.filter(s -> !s.isEmpty() && s.length() > 10)
					.toList();

			if (claims.isEmpty()) return 1.0;

			int verified = 0;
			for (String claim : claims.subList(0, Math.min(5, claims.size()))) {
				String verifyPrompt = """
						Does the following context support this claim? Answer ONLY 'YES' or 'NO'.
						
						Context: %s
						
						Claim: %s""".formatted(truncate(context, 1000), claim);

				String verdict = fastModel.chat(verifyPrompt).trim().toUpperCase();
				if (verdict.contains("YES")) verified++;
			}

			int checked = Math.min(5, claims.size());
			return (double) verified / checked;
		} catch (Exception e) {
			log.warn("Claim verification failed: {}", e.getMessage());
			return 0.5;
		}
	}

	/**
	 * Strategy 3: Consistency check — ask the same question twice and compare.
	 * (Kept as a separate public method since it requires the original question.)
	 */
	public double consistencyCheck(String question, String context) {
		try {
			String prompt = "Based on this context, answer briefly.\n\nContext: "
					+ truncate(context, 1000) + "\n\nQuestion: " + question;

			String answer1 = fastModel.chat(prompt);
			String answer2 = fastModel.chat(prompt);

			Set<String> words1 = new HashSet<>(Arrays.asList(answer1.toLowerCase().split("\\W+")));
			Set<String> words2 = new HashSet<>(Arrays.asList(answer2.toLowerCase().split("\\W+")));

			Set<String> intersection = new HashSet<>(words1);
			intersection.retainAll(words2);
			Set<String> union = new HashSet<>(words1);
			union.addAll(words2);

			return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
		} catch (Exception e) {
			log.warn("Consistency check failed: {}", e.getMessage());
			return 0.5;
		}
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record DetectionResult(
			double entityGroundingScore,
			double claimVerificationScore,
			double overallConfidence,
			String riskLevel
	) {}
}
