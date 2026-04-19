package com.hs.spring_ai_research.agent;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: LLM-as-Judge / Quality Gate
 *
 * <p>The Reviewer is the third agent in the pipeline. It evaluates the Writer's report
 * against a structured rubric (1-10 scale per dimension):</p>
 * <ul>
 *   <li>Faithfulness — is every claim grounded in the sources?</li>
 *   <li>Relevance — does the report answer the question?</li>
 *   <li>Completeness — are all available sources utilized?</li>
 *   <li>Clarity — is the report well-structured?</li>
 *   <li>Hallucination-free — does the report avoid fabricated information?</li>
 * </ul>
 *
 * <p>Verdicts: {@code PASS} (score >= 7), {@code NEEDS_REVISION} (5-7),
 * {@code FAIL} (< 5). On NEEDS_REVISION, the Orchestrator loops back to the Writer.</p>
 *
 * <p>Compare with {@link StructuredReviewerService} which does the same thing
 * declaratively via {@code @AiService} and returns a Java POJO.</p>
 */
@Slf4j
@Component
public class ReviewerAgent {

	private final ChatModel powerfulModel;
	private final String systemPrompt;

	public ReviewerAgent(
			@Qualifier("powerfulModel") ChatModel powerfulModel,
			@Value("classpath:prompts/reviewer-system.txt") Resource systemPromptResource) throws Exception {
		this.powerfulModel = powerfulModel;
		this.systemPrompt = new String(systemPromptResource.getContentAsByteArray());
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Reviews a report by comparing it against the original question and research sources.
	 *
	 * @param originalQuestion  the user's original question
	 * @param researchFindings  the Researcher's brief (ground truth for faithfulness)
	 * @param report            the Writer's report to evaluate
	 * @return verdict (PASS / NEEDS_REVISION / FAIL) with overall score
	 */
	public ReviewResult review(String originalQuestion, String researchFindings, String report) {
		log.info("Reviewer Agent: reviewing report for question '{}'", originalQuestion);

		String fullPrompt = """
				%s

				## Original Research Question
				%s

				## Research Findings (Sources)
				%s

				## Report to Review
				%s""".formatted(systemPrompt, originalQuestion, researchFindings, report);

		String reviewJson = powerfulModel.chat(fullPrompt);
		String cleaned = stripMarkdownFences(reviewJson);
		log.info("Reviewer Agent: review complete");

		return new ReviewResult(cleaned, extractVerdict(cleaned), extractOverallScore(cleaned));
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	/** Finds the verdict string in the JSON response. */
	private String extractVerdict(String reviewJson) {
		if (reviewJson.contains("\"PASS\"")) return "PASS";
		if (reviewJson.contains("\"FAIL\"")) return "FAIL";
		return "NEEDS_REVISION";
	}

	/**
	 * Extracts the numeric "overallScore" value from the JSON response using
	 * simple string parsing (avoids adding a JSON library dependency for one field).
	 */
	private double extractOverallScore(String reviewJson) {
		try {
			int idx = reviewJson.indexOf("\"overallScore\"");
			if (idx == -1) return 0.0;
			String afterKey = reviewJson.substring(idx + 15);
			int colonIdx = afterKey.indexOf(':');
			if (colonIdx == -1) return 0.0;
			String valueStr = afterKey.substring(colonIdx + 1).trim();
			StringBuilder number = new StringBuilder();
			for (char c : valueStr.toCharArray()) {
				if (Character.isDigit(c) || c == '.') {
					number.append(c);
				} else if (!number.isEmpty()) {
					break;
				}
			}
			return Double.parseDouble(number.toString());
		} catch (Exception e) {
			log.warn("Could not parse overall score from review, defaulting to 0: {}", e.getMessage());
			return 0.0;
		}
	}

	/** Strips markdown code fences from LLM responses. */
	private String stripMarkdownFences(String response) {
		String cleaned = response.trim();
		if (cleaned.startsWith("```json")) {
			cleaned = cleaned.substring(7);
		} else if (cleaned.startsWith("```")) {
			cleaned = cleaned.substring(3);
		}
		if (cleaned.endsWith("```")) {
			cleaned = cleaned.substring(0, cleaned.length() - 3);
		}
		return cleaned.trim();
	}

	// ── Result record ───────────────────────────────────────────────────────────

	/**
	 * Output of the Reviewer agent.
	 *
	 * @param reviewJson   full JSON review with per-dimension scores
	 * @param verdict      PASS, NEEDS_REVISION, or FAIL
	 * @param overallScore numeric score (0-10)
	 */
	public record ReviewResult(
			String reviewJson,
			String verdict,
			double overallScore
	) {}
}
