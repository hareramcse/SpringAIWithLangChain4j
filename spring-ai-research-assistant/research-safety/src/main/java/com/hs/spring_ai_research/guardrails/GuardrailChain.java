package com.hs.spring_ai_research.guardrails;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Chains all guardrails together into a single pipeline:
 * - Input guardrails: run BEFORE the LLM call (injection detection, PII redaction)
 * - Output guardrails: run AFTER the LLM call (content moderation)
 *
 * Flow: User Input -> [Injection Check] -> [PII Redaction] -> LLM -> [Content Moderation] -> User
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardrailChain {

	private final PromptInjectionDetector injectionDetector;
	private final PiiRedactor piiRedactor;
	private final ContentModerator contentModerator;
	private final OpenAiModerationGuardrail openAiModeration;

	/**
	 * Run all input guardrails. Returns the (possibly redacted) safe input,
	 * or throws if the input is blocked.
	 */
	public InputGuardrailResult processInput(String userInput) {
		log.debug("Running input guardrail chain...");

		// Layer 1: OpenAI Moderation API (FREE, fast, broad safety check)
		GuardrailResult moderationResult = openAiModeration.moderate(userInput);
		if (moderationResult.blocked()) {
			log.warn("Input BLOCKED by OpenAI Moderation API: {}", moderationResult.reason());
			return new InputGuardrailResult(true, moderationResult, userInput, 0);
		}

		// Layer 2: Custom prompt injection detection (pattern + LLM-based)
		GuardrailResult injectionResult = injectionDetector.check(userInput);
		if (injectionResult.blocked()) {
			log.warn("Input BLOCKED by injection detector: {}", injectionResult.reason());
			return new InputGuardrailResult(true, injectionResult, userInput, 0);
		}

		// Layer 3: PII redaction (transform, not block)
		PiiRedactor.RedactionResult redactionResult = piiRedactor.redact(userInput);
		log.debug("PII redaction complete: {} items redacted", redactionResult.redactionCount());

		return new InputGuardrailResult(false, null,
				redactionResult.redactedText(), redactionResult.redactionCount());
	}

	/**
	 * Run output guardrails on the LLM response.
	 */
	public GuardrailResult processOutput(String llmOutput) {
		log.debug("Running output guardrail chain...");

		// Layer 1: Free OpenAI Moderation API on output
		GuardrailResult moderationResult = openAiModeration.moderate(llmOutput);
		if (moderationResult.blocked()) {
			return moderationResult;
		}

		// Layer 2: Custom LLM-based content moderation (catches domain-specific issues)
		return contentModerator.checkOutput(llmOutput);
	}

	public record InputGuardrailResult(
			boolean blocked,
			GuardrailResult blockReason,
			String processedInput,
			int piiRedactionCount
	) {}
}
