package com.hs.spring_ai_research.constraints;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;


import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks elapsed time per request phase and enforces latency budgets.
 *
 * In production AI systems, you need to know WHERE time is being spent:
 * - RAG retrieval: typically 200-500ms
 * - LLM generation: typically 1-5 seconds
 * - Post-processing: typically <100ms
 *
 * When p95 latency exceeds your target, this tracker tells you WHICH phase
 * is the bottleneck. This is how you answer "what happens at 500 RPS?"
 *
 * Publishes metrics that can be scraped by Prometheus/Grafana via Actuator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LatencyBudgetTracker {

	private final RequestConstraintsConfig config;

	private final ConcurrentHashMap<String, RequestTimeline> activeRequests = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<CompletedRequest> history = new CopyOnWriteArrayList<>();
	private final AtomicLong budgetExceededCount = new AtomicLong(0);

	/**
	 * Start tracking a new request.
	 */
	public String startRequest(String requestId) {
		activeRequests.put(requestId, new RequestTimeline(System.currentTimeMillis()));
		return requestId;
	}

	/**
	 * Record a phase completion within a request.
	 */
	public void recordPhase(String requestId, String phaseName, long durationMs) {
		RequestTimeline timeline = activeRequests.get(requestId);
		if (timeline != null) {
			timeline.phases.put(phaseName, durationMs);
		}
	}

	/**
	 * Check if the remaining budget allows another phase.
	 * Returns the milliseconds remaining, or 0 if budget exceeded.
	 */
	public long getRemainingBudget(String requestId) {
		RequestTimeline timeline = activeRequests.get(requestId);
		if (timeline == null) return config.getP95LatencyMs();

		long elapsed = System.currentTimeMillis() - timeline.startTime;
		long remaining = config.getP95LatencyMs() - elapsed;

		if (remaining <= 0) {
			log.warn("Request {} exceeded latency budget ({}ms > {}ms target)",
					requestId, elapsed, config.getP95LatencyMs());
		}
		return Math.max(0, remaining);
	}

	/**
	 * Complete a request and archive its timeline.
	 */
	public CompletedRequest completeRequest(String requestId) {
		RequestTimeline timeline = activeRequests.remove(requestId);
		if (timeline == null) return null;

		long totalDuration = System.currentTimeMillis() - timeline.startTime;
		boolean exceeded = totalDuration > config.getP95LatencyMs();

		if (exceeded) {
			budgetExceededCount.incrementAndGet();
			log.warn("Request {} completed in {}ms (EXCEEDED budget of {}ms). Phases: {}",
					requestId, totalDuration, config.getP95LatencyMs(), timeline.phases);
		} else {
			log.debug("Request {} completed in {}ms (within budget). Phases: {}",
					requestId, totalDuration, config.getP95LatencyMs(), timeline.phases);
		}

		CompletedRequest completed = new CompletedRequest(requestId, totalDuration,
				new ConcurrentHashMap<>(timeline.phases), exceeded);
		history.add(completed);

		if (history.size() > 1000) {
			history.subList(0, history.size() - 500).clear();
		}

		return completed;
	}

	public LatencyStats getStats() {
		if (history.isEmpty()) {
			return new LatencyStats(0, 0, 0, 0, 0, config.getP95LatencyMs());
		}

		long[] durations = history.stream().mapToLong(CompletedRequest::totalDurationMs).sorted().toArray();
		double avg = history.stream().mapToLong(CompletedRequest::totalDurationMs).average().orElse(0);
		long p50 = durations[durations.length / 2];
		long p95 = durations[(int) (durations.length * 0.95)];
		long p99 = durations[(int) (durations.length * 0.99)];

		return new LatencyStats(history.size(), (long) avg, p50, p95, p99, config.getP95LatencyMs());
	}

	public long getBudgetExceededCount() {
		return budgetExceededCount.get();
	}

	private static class RequestTimeline {
		final long startTime;
		final ConcurrentHashMap<String, Long> phases = new ConcurrentHashMap<>();

		RequestTimeline(long startTime) { this.startTime = startTime; }
	}

	public record CompletedRequest(
			String requestId, long totalDurationMs,
			Map<String, Long> phaseDurations, boolean exceededBudget
	) {}

	public record LatencyStats(
			long totalRequests, long avgMs, long p50Ms,
			long p95Ms, long p99Ms, long budgetMs
	) {}
}
