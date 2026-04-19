package com.hs.spring_ai_research.constraints;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;


import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Backpressure handling for LLM request queuing.
 *
 * When incoming request rate exceeds processing capacity, requests queue up.
 * Without backpressure, the queue grows unbounded -> OOM or cascading timeouts.
 *
 * This handler provides:
 * - Bounded queue with configurable capacity
 * - Rejection policy when queue is full (return 503 immediately)
 * - Queue depth monitoring for alerting
 *
 * The pattern: fast request intake -> bounded queue -> slow LLM processing
 * This is the answer to "what happens when LLM latency spikes?"
 */
@Slf4j
@Service
public class BackpressureHandler {

	private final BlockingQueue<Runnable> taskQueue;
	private final ExecutorService executor;
	private final int capacity;
	private final AtomicLong totalSubmitted = new AtomicLong(0);
	private final AtomicLong totalRejected = new AtomicLong(0);
	private final AtomicLong totalCompleted = new AtomicLong(0);

	public BackpressureHandler(RequestConstraintsConfig config) {
		this.capacity = config.getQueueCapacity();
		this.taskQueue = new ArrayBlockingQueue<>(capacity);
		this.executor = Executors.newFixedThreadPool(
				config.getMaxConcurrentLlmCalls(),
				Thread.ofVirtual().name("llm-worker-", 0).factory()
		);
		log.info("Backpressure handler initialized: queue capacity={}, workers={}",
				capacity, config.getMaxConcurrentLlmCalls());
	}

	/**
	 * Submit a task to the bounded queue. Returns a future with the result.
	 * Throws RejectedExecutionException if queue is full.
	 */
	public <T> CompletableFuture<T> submit(Supplier<T> task) {
		if (taskQueue.size() >= capacity) {
			totalRejected.incrementAndGet();
			log.warn("Backpressure: queue full ({}/{}), rejecting request", taskQueue.size(), capacity);
			throw new RejectedExecutionException("Request queue is full. Try again later.");
		}

		totalSubmitted.incrementAndGet();
		CompletableFuture<T> future = new CompletableFuture<>();

		executor.submit(() -> {
			try {
				T result = task.get();
				future.complete(result);
				totalCompleted.incrementAndGet();
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
		});

		return future;
	}

	/**
	 * Check if the system can accept more work.
	 */
	public boolean canAccept() {
		return taskQueue.size() < capacity;
	}

	public QueueStats getStats() {
		return new QueueStats(
				capacity, taskQueue.size(),
				totalSubmitted.get(), totalCompleted.get(), totalRejected.get(),
				capacity > 0 ? (double) taskQueue.size() / capacity : 0
		);
	}

	@PreDestroy
	public void shutdown() {
		executor.shutdown();
	}

	public record QueueStats(
			int capacity, int currentDepth,
			long totalSubmitted, long totalCompleted, long totalRejected,
			double utilizationPercent
	) {}
}
