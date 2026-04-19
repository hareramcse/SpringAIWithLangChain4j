package com.hs.spring_ai_research.guardrails;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Output guardrail: checks LLM-generated responses for harmful, inappropriate,
 * or off-topic content before returning to the user.
 */
@Slf4j
@Component
public class ContentModerator {

	private final ChatModel fastModel;

	public ContentModerator(@Qualifier("fastModel") ChatModel fastModel) {
		this.fastModel = fastModel;
	}

	public GuardrailResult checkOutput(String output) {
		String prompt = """
				Analyze the following AI-generated response and determine if it contains any:
				1. Harmful or dangerous instructions
				2. Hate speech or discriminatory content
				3. Personal attacks or harassment
				4. Misinformation presented as fact with high confidence
				5. Content that attempts to manipulate the user

				Respond with ONLY "SAFE" or "FLAGGED: [brief reason]".

				AI Response: %s

				Verdict:""".formatted(truncate(output, 1000));

		try {
			String response = fastModel.chat(prompt).trim();
			if (response.toUpperCase().startsWith("FLAGGED")) {
				log.warn("Content moderation FLAGGED output: {}", response);
				return new GuardrailResult(true, "CONTENT_MODERATION", response);
			}
		} catch (Exception e) {
			log.error("Content moderation check failed, allowing output: {}", e.getMessage());
		}
		return GuardrailResult.safe();
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}
}
