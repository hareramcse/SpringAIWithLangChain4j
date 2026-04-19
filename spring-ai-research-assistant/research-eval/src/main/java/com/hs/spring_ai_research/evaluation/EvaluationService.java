package com.hs.spring_ai_research.evaluation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM-as-Judge evaluation pipeline.
 *
 * Uses a powerful LLM to evaluate RAG output quality across three dimensions:
 * 1. Faithfulness — is the answer grounded in the retrieved context?
 * 2. Relevance — does the answer address the original question?
 * 3. Hallucination — does the answer contain fabricated information?
 *
 * This approach is inspired by RAGAS (Retrieval Augmented Generation Assessment)
 * and is the industry standard for automated RAG evaluation.
 */
@Slf4j
@Service
public class EvaluationService {

	private final ChatModel powerfulModel;

	public EvaluationService(@Qualifier("powerfulModel") ChatModel powerfulModel) {
		this.powerfulModel = powerfulModel;
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Evaluates a RAG pipeline output across three dimensions.
	 *
	 * @param question          the original user question
	 * @param retrievedContext   the context retrieved by the RAG pipeline
	 * @param generatedAnswer    the answer produced by the LLM
	 * @return scores (1-10 each), overall average, and verdict (GOOD / ACCEPTABLE / POOR)
	 */
	public EvalResult evaluate(String question, String retrievedContext, String generatedAnswer) {
		log.info("Running evaluation for question: '{}'", question);

		int faithfulness = scoreFaithfulness(question, retrievedContext, generatedAnswer);
		int relevance = scoreRelevance(question, generatedAnswer);
		int hallucination = scoreHallucination(retrievedContext, generatedAnswer);

		double overall = (faithfulness + relevance + hallucination) / 3.0;
		String verdict = overall >= 7 ? "GOOD" : overall >= 5 ? "ACCEPTABLE" : "POOR";

		EvalResult result = new EvalResult(
				faithfulness, relevance, hallucination,
				overall, verdict
		);

		log.info("Evaluation complete: faithfulness={}, relevance={}, hallucination={}, overall={}, verdict={}",
				faithfulness, relevance, hallucination, String.format("%.1f", overall), verdict);
		return result;
	}

	// ── Scoring dimensions ─────────────────────────────────────────────────────

	/** Faithfulness: Is every claim in the answer supported by the context? */
	private int scoreFaithfulness(String question, String context, String answer) {
		String prompt = """
				You are evaluating the faithfulness of an AI-generated answer.
				Faithfulness measures whether every claim in the answer is supported by the provided context.

				Question: %s

				Context (retrieved documents):
				%s

				Answer to evaluate:
				%s

				Score from 1-10 where:
				- 10: Every statement is directly supported by the context
				- 7-9: Most claims supported, minor unsupported generalizations
				- 4-6: Several claims lack support
				- 1-3: Significant fabrication

				Respond with ONLY a single integer (1-10):""".formatted(question, truncate(context, 2000), answer);

		return parseScore(powerfulModel.chat(prompt));
	}

	/** Relevance: Does the answer actually address the question? */
	private int scoreRelevance(String question, String answer) {
		String prompt = """
				You are evaluating the relevance of an AI-generated answer.
				Relevance measures how well the answer addresses the original question.

				Question: %s

				Answer to evaluate:
				%s

				Score from 1-10 where:
				- 10: Directly and comprehensively answers the question
				- 7-9: Answers the main question, may miss minor aspects
				- 4-6: Partially relevant
				- 1-3: Off-topic or barely addresses the question

				Respond with ONLY a single integer (1-10):""".formatted(question, answer);

		return parseScore(powerfulModel.chat(prompt));
	}

	/** Hallucination: Does the answer contain information NOT in the context? */
	private int scoreHallucination(String context, String answer) {
		String prompt = """
				You are checking for hallucinations in an AI-generated answer.
				A hallucination is any factual claim in the answer that is NOT supported by the context.

				Context (the only source of truth):
				%s

				Answer to check:
				%s

				Score from 1-10 where:
				- 10: No hallucinations — everything is in the context
				- 7-9: Minor embellishments that don't change meaning
				- 4-6: Some statements go beyond the context
				- 1-3: Contains clearly fabricated facts

				Respond with ONLY a single integer (1-10):""".formatted(truncate(context, 2000), answer);

		return parseScore(powerfulModel.chat(prompt));
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	private int parseScore(String response) {
		try {
			String digits = response.trim().replaceAll("[^0-9]", "");
			if (digits.isEmpty()) return 5;
			int score = Integer.parseInt(digits.length() > 2 ? digits.substring(0, 2) : digits);
			return Math.max(1, Math.min(10, score));
		} catch (Exception e) {
			log.warn("Failed to parse eval score '{}', defaulting to 5", response);
			return 5;
		}
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	// ── Result record ───────────────────────────────────────────────────────────

	public record EvalResult(
			int faithfulness,
			int relevance,
			int hallucinationFree,
			double overallScore,
			String verdict
	) {}
}
