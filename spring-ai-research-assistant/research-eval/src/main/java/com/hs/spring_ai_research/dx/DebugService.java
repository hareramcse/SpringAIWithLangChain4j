package com.hs.spring_ai_research.dx;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Debug service that captures the full reasoning chain for AI requests.
 *
 * When enabled, captures every step of processing:
 * Input -> Guardrails -> RAG chunks -> Prompt sent -> Raw LLM response -> Post-processing -> Output
 *
 * This answers: "how do team members debug AI failures?"
 * Answer: enable debug mode, reproduce the issue, then inspect the full chain
 * to see exactly what went wrong at which step.
 *
 * Each debug trace is stored by request ID and is queryable via the API.
 * In production, this would integrate with distributed tracing (Jaeger/Zipkin).
 */
@Slf4j
@Service
public class DebugService {

	private final AtomicBoolean debugEnabled = new AtomicBoolean(false);
	private final ConcurrentHashMap<String, DebugTrace> traces = new ConcurrentHashMap<>();

	public boolean isEnabled() {
		return debugEnabled.get();
	}

	public void setEnabled(boolean enabled) {
		debugEnabled.set(enabled);
		log.info("Debug mode {}", enabled ? "ENABLED" : "DISABLED");
	}

	/**
	 * Start a new debug trace for a request.
	 */
	public DebugTrace startTrace(String requestId) {
		if (!debugEnabled.get()) return null;

		DebugTrace trace = new DebugTrace(requestId, Instant.now());
		traces.put(requestId, trace);
		return trace;
	}

	/**
	 * Add a step to an active trace.
	 */
	public void addStep(String requestId, String stepName, String input, String output, long durationMs) {
		DebugTrace trace = traces.get(requestId);
		if (trace == null) return;

		trace.steps.add(new DebugStep(stepName, input, output, durationMs, Instant.now()));
		log.debug("Debug step: {} -> {} ({}ms)", requestId, stepName, durationMs);
	}

	/**
	 * Complete a trace.
	 */
	public DebugTrace completeTrace(String requestId, String finalOutput) {
		DebugTrace trace = traces.get(requestId);
		if (trace == null) return null;

		trace.completedAt = Instant.now();
		trace.finalOutput = finalOutput;
		trace.totalDurationMs = java.time.Duration.between(trace.startedAt, trace.completedAt).toMillis();

		// Keep last 100 traces
		if (traces.size() > 100) {
			String oldest = traces.keySet().iterator().next();
			traces.remove(oldest);
		}

		return trace;
	}

	public DebugTrace getTrace(String requestId) {
		return traces.get(requestId);
	}

	public Map<String, String> listTraces() {
		Map<String, String> summary = new ConcurrentHashMap<>();
		traces.forEach((k, v) -> summary.put(k, v.steps.size() + " steps, " +
				(v.completedAt != null ? "completed" : "in-progress")));
		return summary;
	}

	public static class DebugTrace {
		public final String requestId;
		public final Instant startedAt;
		public final List<DebugStep> steps = new ArrayList<>();
		public Instant completedAt;
		public String finalOutput;
		public long totalDurationMs;

		DebugTrace(String requestId, Instant startedAt) {
			this.requestId = requestId;
			this.startedAt = startedAt;
		}
	}

	public record DebugStep(String stepName, String input, String output, long durationMs, Instant timestamp) {}
}
