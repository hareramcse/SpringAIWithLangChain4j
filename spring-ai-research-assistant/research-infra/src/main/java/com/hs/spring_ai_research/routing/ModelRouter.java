package com.hs.spring_ai_research.routing;

import java.util.Set;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Routes requests to appropriate models based on query complexity.
 *
 * Complexity is scored using heuristics:
 * - Query length (longer = more complex)
 * - Presence of analytical keywords
 * - Number of sub-questions
 *
 * Complex queries go to gpt-4o (better reasoning, higher cost).
 * Simple queries go to gpt-4o-mini (faster, cheaper).
 *
 * This is a key production concern: using the right model for the right task
 * can reduce costs by 50-80% without sacrificing quality.
 */
@Slf4j
@Service
public class ModelRouter {

	private final ChatModel powerfulModel;
	private final ChatModel fastModel;
	private final StreamingChatModel powerfulStreamingModel;
	private final StreamingChatModel fastStreamingModel;
	private final double complexityThreshold;

	private static final Set<String> COMPLEXITY_KEYWORDS = Set.of(
			"compare", "analyze", "evaluate", "explain why", "trade-offs",
			"advantages and disadvantages", "in-depth", "comprehensive",
			"architecture", "design", "strategy", "how does", "what are the implications",
			"critically", "assessment", "multi-step", "relationship between"
	);

	public ModelRouter(
			@Qualifier("powerfulModel") ChatModel powerfulModel,
			@Qualifier("fastModel") ChatModel fastModel,
			@Qualifier("powerfulStreamingModel") StreamingChatModel powerfulStreamingModel,
			@Qualifier("fastStreamingModel") StreamingChatModel fastStreamingModel,
			@Value("${app.models.complexity-threshold}") double complexityThreshold) {
		this.powerfulModel = powerfulModel;
		this.fastModel = fastModel;
		this.powerfulStreamingModel = powerfulStreamingModel;
		this.fastStreamingModel = fastStreamingModel;
		this.complexityThreshold = complexityThreshold;
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/** Routes to the appropriate synchronous model based on query complexity. */
	public ChatModel route(String query) {
		double score = scoreComplexity(query);
		boolean usePowerful = score >= complexityThreshold;
		log.info("Model routing: complexity={}, threshold={}, selected={}",
				String.format("%.2f", score), complexityThreshold, usePowerful ? "gpt-4o" : "gpt-4o-mini");
		return usePowerful ? powerfulModel : fastModel;
	}

	/** Routes to the appropriate streaming model (for SSE endpoints). */
	public StreamingChatModel routeStreaming(String query) {
		double score = scoreComplexity(query);
		return score >= complexityThreshold ? powerfulStreamingModel : fastStreamingModel;
	}

	/** Returns the model name that would be selected (for logging/metadata). */
	public String getRoutedModelName(String query) {
		double score = scoreComplexity(query);
		return score >= complexityThreshold ? "gpt-4o" : "gpt-4o-mini";
	}

	// ── Complexity scoring ─────────────────────────────────────────────────────

	/**
	 * Scores query complexity from 0.0 to 1.0 using four heuristics:
	 * query length, analytical keywords, question count, and clause separators.
	 */
	public double scoreComplexity(String query) {
		String lower = query.toLowerCase();
		double score = 0.0;

		// Length factor (longer queries tend to be more complex)
		if (query.length() > 200) score += 0.3;
		else if (query.length() > 100) score += 0.2;
		else if (query.length() > 50) score += 0.1;

		// Keyword factor
		long keywordMatches = COMPLEXITY_KEYWORDS.stream()
				.filter(lower::contains)
				.count();
		score += Math.min(keywordMatches * 0.15, 0.45);

		// Question count factor (multiple questions = more complex)
		long questionMarks = query.chars().filter(c -> c == '?').count();
		if (questionMarks > 1) score += 0.2;

		// Sub-clause factor (commas, semicolons indicate complex structure)
		long separators = query.chars().filter(c -> c == ',' || c == ';').count();
		if (separators > 3) score += 0.1;

		return Math.min(score, 1.0);
	}
}
