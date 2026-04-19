package com.hs.spring_ai_research.routing;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Retry / Fallback / Circuit Breaker
 *
 * Wraps a primary ChatModel with resilience patterns:
 *
 * 1. RETRY: If the primary model fails, retry up to N times with exponential backoff.
 * 2. FALLBACK: If retries are exhausted, fall back to a cheaper/different model.
 * 3. CIRCUIT BREAKER: After N consecutive failures, automatically route ALL requests
 *    to the fallback for a cooldown period, then try primary again.
 *
 * Why this matters in production:
 * - API rate limits can cause transient failures
 * - Model endpoints can have temporary outages
 * - Graceful degradation > total failure
 * - Users get a response (possibly from a less powerful model) instead of an error
 */
@Slf4j
public class ResilientModelWrapper {

	private final ChatModel primaryModel;
	private final ChatModel fallbackModel;
	private final String primaryName;
	private final String fallbackName;
	private final int maxRetries;

	private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
	private final AtomicLong circuitOpenUntil = new AtomicLong(0);

	/** Circuit breaker trips after this many consecutive primary failures. */
	private static final int CIRCUIT_BREAKER_THRESHOLD = 3;

	/** When tripped, all requests go to fallback for this duration. */
	private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 60_000;

	public ResilientModelWrapper(ChatModel primaryModel, ChatModel fallbackModel,
								 String primaryName, String fallbackName, int maxRetries) {
		this.primaryModel = primaryModel;
		this.fallbackModel = fallbackModel;
		this.primaryName = primaryName;
		this.fallbackName = fallbackName;
		this.maxRetries = maxRetries;
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Sends a prompt to the primary model with retry + fallback + circuit breaker.
	 *
	 * @param prompt the complete prompt to send
	 * @return response including which model was used and whether fallback was invoked
	 */
	public ResilientResponse chat(String prompt) {
		if (isCircuitOpen()) {
			log.warn("Circuit breaker OPEN — routing directly to fallback ({})", fallbackName);
			return chatWithFallback(prompt);
		}

		for (int attempt = 1; attempt <= maxRetries; attempt++) {
			try {
				String response = primaryModel.chat(prompt);
				consecutiveFailures.set(0);
				return new ResilientResponse(response, primaryName, attempt, false);
			} catch (Exception e) {
				log.warn("Primary model ({}) attempt {}/{} failed: {}",
						primaryName, attempt, maxRetries, e.getMessage());
				if (attempt < maxRetries) {
					sleep(attempt);
				}
			}
		}

		int failures = consecutiveFailures.incrementAndGet();
		if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
			circuitOpenUntil.set(System.currentTimeMillis() + CIRCUIT_BREAKER_COOLDOWN_MS);
			log.error("Circuit breaker TRIPPED after {} consecutive failures. " +
					"Cooldown for {}ms", failures, CIRCUIT_BREAKER_COOLDOWN_MS);
		}

		return chatWithFallback(prompt);
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	private ResilientResponse chatWithFallback(String prompt) {
		try {
			String response = fallbackModel.chat(prompt);
			return new ResilientResponse(response, fallbackName, 1, true);
		} catch (Exception e) {
			log.error("Fallback model ({}) also failed: {}", fallbackName, e.getMessage());
			throw new RuntimeException("Both primary and fallback models failed", e);
		}
	}

	private boolean isCircuitOpen() {
		long openUntil = circuitOpenUntil.get();
		if (openUntil == 0) return false;
		if (System.currentTimeMillis() > openUntil) {
			circuitOpenUntil.set(0);
			consecutiveFailures.set(0);
			log.info("Circuit breaker RESET — trying primary model again");
			return false;
		}
		return true;
	}

	private void sleep(int attempt) {
		try {
			long delay = (long) Math.pow(2, attempt) * 500;
			log.debug("Retrying in {}ms...", delay);
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ── Result record ───────────────────────────────────────────────────────────

	/**
	 * @param content      LLM response text
	 * @param modelUsed    which model produced the response (primary or fallback name)
	 * @param attempts     how many attempts were made on the primary model
	 * @param usedFallback true if the response came from the fallback model
	 */
	public record ResilientResponse(
			String content,
			String modelUsed,
			int attempts,
			boolean usedFallback
	) {}
}
