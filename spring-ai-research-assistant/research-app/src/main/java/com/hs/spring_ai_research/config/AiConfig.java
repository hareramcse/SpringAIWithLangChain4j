package com.hs.spring_ai_research.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hs.spring_ai_research.routing.ResilientModelWrapper;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates all LLM model beans used throughout the application.
 *
 * <p>Bean overview:</p>
 * <ul>
 *   <li>{@code powerfulModel} / {@code powerfulStreamingModel} —
 *       gpt-4o for complex tasks (research, review, evaluation)</li>
 *   <li>{@code fastModel} / {@code fastStreamingModel} —
 *       gpt-4o-mini for simple tasks (classification, guardrails, caching)</li>
 *   <li>{@code resilientModel} —
 *       wraps powerful + fast with retry, fallback, and circuit breaker</li>
 * </ul>
 *
 * <p>Temperature is set to 0.0 for deterministic, reproducible output.</p>
 */
@Slf4j
@Configuration
public class AiConfig {

	@Value("${langchain4j.open-ai.chat-model.api-key}")
	private String apiKey;

	// ── Synchronous models (used by most agents and services) ───────────────────

	/** gpt-4o — best reasoning, used for research, writing, and reviewing. */
	@Bean("powerfulModel")
	public ChatModel powerfulModel(
			@Value("${app.models.powerful}") String modelName) {
		log.info("Initializing powerful model: {}", modelName);
		return OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName(modelName)
				.temperature(0.0)
				.maxTokens(4000)
				.build();
	}

	/** gpt-4o-mini — faster and cheaper, used for simple tasks and fallback. */
	@Bean("fastModel")
	public ChatModel fastModel(
			@Value("${app.models.fast}") String modelName) {
		log.info("Initializing fast model: {}", modelName);
		return OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName(modelName)
				.temperature(0.0)
				.maxTokens(2000)
				.build();
	}

	// ── Streaming models (used for SSE endpoints) ───────────────────────────────

	@Bean("powerfulStreamingModel")
	public StreamingChatModel powerfulStreamingModel(
			@Value("${app.models.powerful}") String modelName) {
		return OpenAiStreamingChatModel.builder()
				.apiKey(apiKey)
				.modelName(modelName)
				.temperature(0.0)
				.maxTokens(4000)
				.build();
	}

	@Bean("fastStreamingModel")
	public StreamingChatModel fastStreamingModel(
			@Value("${app.models.fast}") String modelName) {
		return OpenAiStreamingChatModel.builder()
				.apiKey(apiKey)
				.modelName(modelName)
				.temperature(0.0)
				.maxTokens(2000)
				.build();
	}

	// ── Resilient wrapper (retry + fallback + circuit breaker) ──────────────────

	/**
	 * Wraps the powerful model with automatic retry (2 attempts with backoff),
	 * fallback to the fast model, and a circuit breaker that auto-routes to
	 * the fallback after 3 consecutive failures for 60 seconds.
	 *
	 * @see ResilientModelWrapper
	 */
	@Bean
	public ResilientModelWrapper resilientModel(
			@Qualifier("powerfulModel") ChatModel powerful,
			@Qualifier("fastModel") ChatModel fast) {
		log.info("Initializing resilient model wrapper (primary=gpt-4o, fallback=gpt-4o-mini)");
		return new ResilientModelWrapper(powerful, fast, "gpt-4o", "gpt-4o-mini", 2);
	}
}
