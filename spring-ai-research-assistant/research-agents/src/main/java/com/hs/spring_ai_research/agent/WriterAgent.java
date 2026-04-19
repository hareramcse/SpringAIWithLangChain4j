package com.hs.spring_ai_research.agent;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Few-Shot Prompting + JSON Output
 *
 * <p>The Writer is the second agent in the pipeline. It transforms the Researcher's
 * raw brief into a structured, polished report in JSON format.</p>
 *
 * <p>The system prompt (in {@code resources/prompts/writer-system.txt}) uses few-shot
 * examples to show the LLM exactly what format the report should follow.</p>
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li>{@link #writeReport} — first draft from a research brief</li>
 *   <li>{@link #reviseReport} — revision based on Reviewer feedback (used in the revision loop)</li>
 * </ul>
 */
@Slf4j
@Component
public class WriterAgent {

	private final ChatModel powerfulModel;
	private final String systemPrompt;

	public WriterAgent(
			@Qualifier("powerfulModel") ChatModel powerfulModel,
			@Value("classpath:prompts/writer-system.txt") Resource systemPromptResource) throws Exception {
		this.powerfulModel = powerfulModel;
		this.systemPrompt = new String(systemPromptResource.getContentAsByteArray());
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Generates the initial report from the Researcher's brief.
	 *
	 * @param researchBrief the synthesized research findings
	 * @return a JSON report string (markdown fences stripped)
	 */
	public String writeReport(String researchBrief) {
		log.info("Writer Agent: generating report from research brief ({} chars)", researchBrief.length());

		String fullPrompt = """
				%s

				## Research Brief to Transform
				%s""".formatted(systemPrompt, researchBrief);

		String report = powerfulModel.chat(fullPrompt);
		log.info("Writer Agent: report generated ({} chars)", report.length());
		return stripMarkdownFences(report);
	}

	/**
	 * Revises an existing report based on Reviewer feedback.
	 * Called during the Orchestrator's revision loop.
	 *
	 * @param originalReport  the report to revise
	 * @param reviewFeedback  the Reviewer's JSON feedback
	 * @return a revised JSON report string
	 */
	public String reviseReport(String originalReport, String reviewFeedback) {
		log.info("Writer Agent: revising report based on reviewer feedback");

		String revisionPrompt = """
				%s

				## Previous Report
				%s

				## Reviewer Feedback
				%s

				Please revise the report addressing all the issues raised by the reviewer.
				Maintain the same JSON structure.""".formatted(systemPrompt, originalReport, reviewFeedback);

		String revised = powerfulModel.chat(revisionPrompt);
		log.info("Writer Agent: revision complete ({} chars)", revised.length());
		return stripMarkdownFences(revised);
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	/**
	 * LLMs often wrap JSON in markdown code fences ({@code ```json ... ```}).
	 * This strips them so the result is parseable JSON.
	 */
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
}
