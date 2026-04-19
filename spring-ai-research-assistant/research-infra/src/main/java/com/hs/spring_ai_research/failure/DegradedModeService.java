package com.hs.spring_ai_research.failure;

import java.util.concurrent.atomic.AtomicReference;


import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages service degradation levels based on system health.
 *
 * Four degradation levels:
 * 1. FULL — All features active (normal operation)
 * 2. REDUCED — Skip expensive operations (revision cycles, advanced RAG)
 * 3. MINIMAL — Cache-only responses + fast model only
 * 4. OFFLINE — Static safe defaults, no LLM calls at all
 *
 * The system auto-selects the degradation level based on:
 * - Circuit breaker state (if models are failing)
 * - Average latency (if responses are too slow)
 * - Error rate (if too many requests fail)
 *
 * This is the answer to "what happens when the LLM is slow/down?"
 * instead of crashing, the system gracefully degrades.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DegradedModeService {

	private final AtomicReference<DegradationLevel> currentLevel =
			new AtomicReference<>(DegradationLevel.FULL);

	private int consecutiveErrors = 0;
	private long lastEvalTime = System.currentTimeMillis();

	public enum DegradationLevel {
		FULL("All features active"),
		REDUCED("Skip revision cycles and advanced RAG"),
		MINIMAL("Cache-only + fast model, skip review"),
		OFFLINE("Static responses only, no LLM calls");

		public final String description;
		DegradationLevel(String description) { this.description = description; }
	}

	public DegradationLevel getCurrentLevel() {
		return currentLevel.get();
	}

	/**
	 * Report a successful operation — moves toward FULL.
	 */
	public void reportSuccess() {
		consecutiveErrors = 0;
		DegradationLevel current = currentLevel.get();
		if (current != DegradationLevel.FULL) {
			DegradationLevel improved = switch (current) {
				case OFFLINE -> DegradationLevel.MINIMAL;
				case MINIMAL -> DegradationLevel.REDUCED;
				case REDUCED -> DegradationLevel.FULL;
				default -> current;
			};
			if (currentLevel.compareAndSet(current, improved)) {
				log.info("Degradation level improved: {} -> {}", current, improved);
			}
		}
	}

	/**
	 * Report a failure — may trigger degradation.
	 */
	public void reportFailure(String reason) {
		consecutiveErrors++;
		log.warn("Failure reported (consecutive: {}): {}", consecutiveErrors, reason);

		DegradationLevel newLevel;
		if (consecutiveErrors >= 10) {
			newLevel = DegradationLevel.OFFLINE;
		} else if (consecutiveErrors >= 5) {
			newLevel = DegradationLevel.MINIMAL;
		} else if (consecutiveErrors >= 3) {
			newLevel = DegradationLevel.REDUCED;
		} else {
			return;
		}

		DegradationLevel current = currentLevel.get();
		if (newLevel.ordinal() > current.ordinal() && currentLevel.compareAndSet(current, newLevel)) {
			log.error("Degradation level changed: {} -> {} (reason: {})", current, newLevel, reason);
		}
	}

	/**
	 * Report high latency — may trigger degradation to reduce processing.
	 */
	public void reportHighLatency(long latencyMs, long budgetMs) {
		if (latencyMs > budgetMs * 2) {
			reportFailure("Extreme latency: " + latencyMs + "ms (budget: " + budgetMs + "ms)");
		} else if (latencyMs > budgetMs) {
			DegradationLevel current = currentLevel.get();
			if (current == DegradationLevel.FULL) {
				currentLevel.set(DegradationLevel.REDUCED);
				log.warn("Switching to REDUCED mode due to high latency: {}ms > {}ms", latencyMs, budgetMs);
			}
		}
	}

	/**
	 * Force a specific degradation level (for manual override / testing).
	 */
	public void setLevel(DegradationLevel level) {
		DegradationLevel previous = currentLevel.getAndSet(level);
		log.info("Degradation level manually set: {} -> {}", previous, level);
	}

	public boolean shouldSkipRevision() {
		return currentLevel.get().ordinal() >= DegradationLevel.REDUCED.ordinal();
	}

	public boolean shouldUseCacheOnly() {
		return currentLevel.get().ordinal() >= DegradationLevel.MINIMAL.ordinal();
	}

	public boolean shouldUseStaticDefaults() {
		return currentLevel.get() == DegradationLevel.OFFLINE;
	}
}
