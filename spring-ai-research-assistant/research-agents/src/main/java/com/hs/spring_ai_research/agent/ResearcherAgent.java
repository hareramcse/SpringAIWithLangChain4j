package com.hs.spring_ai_research.agent;

import java.util.List;
import java.util.stream.Collectors;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.hs.spring_ai_research.rag.AdvancedRagService;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Chain-of-Thought (CoT) Prompting + RAG
 *
 * <p>The Researcher is the first agent in the pipeline. It:</p>
 * <ol>
 *   <li>Searches the knowledge base via {@link AdvancedRagService} (hybrid retrieval)</li>
 *   <li>Constructs a prompt with the system prompt + retrieved context + user question</li>
 *   <li>Sends it to gpt-4o with CoT instructions to decompose and synthesize</li>
 *   <li>Returns a structured {@link ResearchResult} with the brief, sources, and confidence</li>
 * </ol>
 *
 * <p>The system prompt lives in {@code resources/prompts/researcher-system.txt} and can be
 * edited without recompiling.</p>
 */
@Slf4j
@Component
public class ResearcherAgent {

	private final ChatModel powerfulModel;
	private final AdvancedRagService ragService;
	private final String systemPrompt;

	public ResearcherAgent(
			@Qualifier("powerfulModel") ChatModel powerfulModel,
			AdvancedRagService ragService,
			@Value("classpath:prompts/researcher-system.txt") Resource systemPromptResource) throws Exception {
		this.powerfulModel = powerfulModel;
		this.ragService = ragService;
		this.systemPrompt = new String(systemPromptResource.getContentAsByteArray());
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Researches the given question by retrieving relevant documents and
	 * synthesizing a research brief via CoT prompting.
	 *
	 * @param question the user's research question
	 * @return research brief, source list, and confidence level (HIGH / MEDIUM / LOW / NONE)
	 */
	public ResearchResult research(String question) {
		log.info("Researcher Agent: starting research for '{}'", question);

		List<TextSegment> retrievedSegments = ragService.retrieve(question);

		if (retrievedSegments.isEmpty()) {
			log.warn("Researcher Agent: no relevant documents found");
			return new ResearchResult(
					"No relevant information found in the knowledge base for this question.",
					List.of(), "NONE");
		}

		String context = ragService.formatAsContext(retrievedSegments);

		String fullPrompt = """
				%s

				## Context from Knowledge Base
				%s

				## User Question
				%s""".formatted(systemPrompt, context, question);

		String researchBrief = powerfulModel.chat(fullPrompt);
		log.info("Researcher Agent: completed research brief ({} chars)", researchBrief.length());

		List<String> sources = retrievedSegments.stream()
				.map(s -> s.metadata().getString("source"))
				.filter(s -> s != null)
				.distinct()
				.collect(Collectors.toList());

		return new ResearchResult(researchBrief, sources, determineConfidence(retrievedSegments));
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	/** Confidence is based on how many relevant chunks were retrieved. */
	private String determineConfidence(List<TextSegment> segments) {
		if (segments.size() >= 4) return "HIGH";
		if (segments.size() >= 2) return "MEDIUM";
		return "LOW";
	}

	// ── Result record ───────────────────────────────────────────────────────────

	/**
	 * Output of the Researcher agent.
	 *
	 * @param brief      synthesized research findings from the knowledge base
	 * @param sources    distinct source names from the retrieved documents
	 * @param confidence HIGH (4+ chunks), MEDIUM (2-3), LOW (1), NONE (empty KB)
	 */
	public record ResearchResult(
			String brief,
			List<String> sources,
			String confidence
	) {}
}
