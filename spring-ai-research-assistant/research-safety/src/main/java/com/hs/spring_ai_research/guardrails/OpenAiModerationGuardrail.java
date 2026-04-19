package com.hs.spring_ai_research.guardrails;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: OpenAI Moderation API
 *
 * Uses OpenAI's dedicated Moderation endpoint to classify content for safety.
 * This is different from our custom ContentModerator (which uses GPT for classification):
 *
 * OpenAI Moderation API:
 * - FREE to use (no token cost)
 * - Purpose-built for safety classification
 * - Detects: hate, harassment, self-harm, sexual, violence, etc.
 * - Very fast (~50ms vs ~1-2s for LLM-based moderation)
 *
 * Custom ContentModerator (LLM-based):
 * - Costs tokens per check
 * - More flexible (custom categories, domain-specific rules)
 * - Slower but can catch nuanced issues
 *
 * Best practice: Use BOTH in a chain — free Moderation API for broad safety,
 * then LLM-based for domain-specific checks only if needed.
 */
@Slf4j
@Component
public class OpenAiModerationGuardrail {

	private final OpenAiModerationModel moderationModel;

	public OpenAiModerationGuardrail(
			@Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey) {
		this.moderationModel = OpenAiModerationModel.builder()
				.apiKey(apiKey)
				.build();
	}

	/**
	 * Check text using OpenAI's free Moderation API.
	 */
	public GuardrailResult moderate(String text) {
		try {
			Response<Moderation> response = moderationModel.moderate(text);
			Moderation moderation = response.content();

			if (moderation.flagged()) {
				log.warn("OpenAI Moderation API FLAGGED content: '{}'", moderation.flaggedText());
				return new GuardrailResult(true, "OPENAI_MODERATION",
						"Content flagged by OpenAI Moderation: " + moderation.flaggedText());
			}

			log.debug("OpenAI Moderation API: content is clean");
			return GuardrailResult.safe();
		} catch (Exception e) {
			log.error("OpenAI Moderation API call failed, allowing content: {}", e.getMessage());
			return GuardrailResult.safe();
		}
	}
}
