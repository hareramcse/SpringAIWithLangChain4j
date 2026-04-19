package com.hs.spring_ai_research.constraints;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;


import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Semaphore-based concurrency control for LLM API calls.
 *
 * Prevents overwhelming the LLM provider with too many concurrent requests.
 * When the limit is reached, new requests either wait or get rejected (429).
 *
 * Why this matters:
 * - LLM APIs have rate limits (e.g., OpenAI: 500 RPM for gpt-4o)
 * - Each concurrent call holds a connection and consumes memory
 * - At 500 RPS, without concurrency control, you'd exceed rate limits instantly
 * - This is the "what happens at scale?" answer interviewers want to hear
 *
 * In production, this would integrate with a distributed rate limiter (Redis-based)
 * for multi-instance deployments.
 */
@Slf4j
@Service
public class ConcurrencyLimiter {

	private final Semaphore semaphore;
	private final int maxConcurrent;
	private final AtomicLong totalAcquired = new AtomicLong(0);
	private final AtomicLong totalRejected = new AtomicLong(0);
	private final AtomicLong totalWaited = new AtomicLong(0);

	public ConcurrencyLimiter(RequestConstraintsConfig config) {
		this.maxConcurrent = config.getMaxConcurrentLlmCalls();
		this.semaphore = new Semaphore(maxConcurrent, true);
		log.info("Concurrency limiter initialized: max {} concurrent LLM calls", maxConcurrent);
	}

	/**
	 * Execute a task with concurrency control. Blocks until a slot is available.
	 */
	public <T> T executeWithLimit(Supplier<T> task) throws InterruptedException {
		int available = semaphore.availablePermits();
		if (available == 0) {
			totalWaited.incrementAndGet();
			log.debug("All {} LLM slots occupied, waiting...", maxConcurrent);
		}

		semaphore.acquire();
		totalAcquired.incrementAndGet();
		try {
			return task.get();
		} finally {
			semaphore.release();
		}
	}

	/**
	 * Try to execute immediately. Returns null if no slot available (for 429 responses).
	 */
	public <T> T tryExecute(Supplier<T> task) {
		if (!semaphore.tryAcquire()) {
			totalRejected.incrementAndGet();
			log.warn("Concurrency limit reached ({}/{}), rejecting request",
					maxConcurrent - semaphore.availablePermits(), maxConcurrent);
			return null;
		}
		totalAcquired.incrementAndGet();
		try {
			return task.get();
		} finally {
			semaphore.release();
		}
	}

	public ConcurrencyStats getStats() {
		return new ConcurrencyStats(
				maxConcurrent,
				maxConcurrent - semaphore.availablePermits(),
				semaphore.availablePermits(),
				totalAcquired.get(),
				totalRejected.get(),
				totalWaited.get()
		);
	}

	public record ConcurrencyStats(
			int maxConcurrent, int currentActive, int available,
			long totalAcquired, long totalRejected, long totalWaited
	) {}
}
