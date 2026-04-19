package com.hs.spring_ai_research.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Intercepts and traces every LLM interaction for observability.
 *
 * Logs: prompt (truncated), response (truncated), model used, token counts,
 * latency, and operation name. In production, these traces would feed into
 * an observability platform (Datadog, New Relic, or OpenTelemetry).
 */
@Slf4j
@Service
public class TraceInterceptor {

	private final CopyOnWriteArrayList<TraceRecord> traces = new CopyOnWriteArrayList<>();
	private final CostTracker costTracker;

	public TraceInterceptor(CostTracker costTracker) {
		this.costTracker = costTracker;
	}

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Wraps an LLM call with full tracing. Use this instead of calling the model
	 * directly to get automatic logging of prompt, response, tokens, latency, and errors.
	 *
	 * @param operation human-readable name (e.g. "research", "review", "guardrail-check")
	 * @param model     model name for cost tracking
	 * @param prompt    the full prompt being sent
	 * @param llmCall   lazy supplier that makes the actual LLM API call
	 * @return the trace record (also stored internally for querying)
	 */
	public TraceRecord trace(String operation, String model, String prompt,
							 java.util.function.Supplier<String> llmCall) {
		Instant start = Instant.now();
		String response = null;
		String error = null;

		try {
			response = llmCall.get();
			return recordSuccess(operation, model, prompt, response, start);
		} catch (Exception e) {
			error = e.getMessage();
			recordFailure(operation, model, prompt, error, start);
			throw e;
		}
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	private TraceRecord recordSuccess(String operation, String model,
									  String prompt, String response, Instant start) {
		long latencyMs = Duration.between(start, Instant.now()).toMillis();
		int estimatedInputTokens = estimateTokens(prompt);
		int estimatedOutputTokens = estimateTokens(response);

		costTracker.recordUsage(model, operation, estimatedInputTokens, estimatedOutputTokens);

		TraceRecord record = new TraceRecord(
				operation, model, truncate(prompt, 200), truncate(response, 200),
				estimatedInputTokens, estimatedOutputTokens,
				latencyMs, true, null, System.currentTimeMillis()
		);
		traces.add(record);

		log.info("[TRACE] op={} model={} latency={}ms tokens={}+{} status=OK",
				operation, model, latencyMs, estimatedInputTokens, estimatedOutputTokens);
		return record;
	}

	private void recordFailure(String operation, String model,
							   String prompt, String error, Instant start) {
		long latencyMs = Duration.between(start, Instant.now()).toMillis();

		TraceRecord record = new TraceRecord(
				operation, model, truncate(prompt, 200), null,
				estimateTokens(prompt), 0,
				latencyMs, false, error, System.currentTimeMillis()
		);
		traces.add(record);

		log.error("[TRACE] op={} model={} latency={}ms status=FAIL error={}",
				operation, model, latencyMs, error);
	}

	/**
	 * Rough token estimation: ~4 characters per token for English text.
	 */
	private int estimateTokens(String text) {
		if (text == null) return 0;
		return Math.max(1, text.length() / 4);
	}

	// ── Query methods ───────────────────────────────────────────────────────────

	public java.util.List<TraceRecord> getRecentTraces(int limit) {
		int size = traces.size();
		int from = Math.max(0, size - limit);
		return traces.subList(from, size);
	}

	public Map<String, Object> getTraceSummary() {
		long totalTraces = traces.size();
		long failures = traces.stream().filter(t -> !t.success).count();
		double avgLatency = traces.stream().mapToLong(TraceRecord::latencyMs).average().orElse(0);

		return Map.of(
				"totalTraces", totalTraces,
				"failures", failures,
				"successRate", totalTraces > 0 ? (double)(totalTraces - failures) / totalTraces : 0,
				"avgLatencyMs", avgLatency
		);
	}

	private String truncate(String text, int maxLength) {
		if (text == null) return null;
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record TraceRecord(
			String operation,
			String model,
			String promptPreview,
			String responsePreview,
			int inputTokens,
			int outputTokens,
			long latencyMs,
			boolean success,
			String error,
			long timestamp
	) {}
}
