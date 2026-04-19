package com.hs.spring_ai_research.observability;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;


import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Tracks token usage and calculates dollar costs for every LLM call.
 *
 * Pricing is based on OpenAI's published rates. In production, this data
 * would feed into dashboards and alerting for cost anomaly detection.
 */
@Slf4j
@Service
public class CostTracker {

	private static final Map<String, ModelPricing> PRICING = Map.of(
			"gpt-4o", new ModelPricing(2.50, 10.00),
			"gpt-4o-mini", new ModelPricing(0.15, 0.60),
			"text-embedding-3-small", new ModelPricing(0.02, 0.0)
	);

	private final CopyOnWriteArrayList<CostEntry> entries = new CopyOnWriteArrayList<>();
	private final ConcurrentHashMap<String, Long> activeTraces = new ConcurrentHashMap<>();
	private final AtomicLong totalInputTokens = new AtomicLong(0);
	private final AtomicLong totalOutputTokens = new AtomicLong(0);

	// ── Trace lifecycle (start/end brackets around a pipeline run) ───────────

	public String startTrace(String operation) {
		String traceId = UUID.randomUUID().toString().substring(0, 8);
		activeTraces.put(traceId, System.currentTimeMillis());
		log.debug("Trace started: {} for operation '{}'", traceId, operation);
		return traceId;
	}

	public void endTrace(String traceId) {
		Long startTime = activeTraces.remove(traceId);
		if (startTime != null) {
			log.debug("Trace ended: {} ({}ms)", traceId, System.currentTimeMillis() - startTime);
		}
	}

	// ── Recording and querying ─────────────────────────────────────────────────

	/** Records token usage for a single LLM call and computes dollar cost. */
	public void recordUsage(String model, String operation, int inputTokens, int outputTokens) {
		totalInputTokens.addAndGet(inputTokens);
		totalOutputTokens.addAndGet(outputTokens);

		ModelPricing pricing = PRICING.getOrDefault(model,
				new ModelPricing(0.0, 0.0));
		double cost = (inputTokens / 1_000_000.0 * pricing.inputPricePerMillion)
				+ (outputTokens / 1_000_000.0 * pricing.outputPricePerMillion);

		CostEntry entry = new CostEntry(
				model, operation, inputTokens, outputTokens,
				cost, System.currentTimeMillis()
		);
		entries.add(entry);

		log.info("Cost tracked: model={}, operation={}, tokens={}+{}, cost=${}",
				model, operation, inputTokens, outputTokens, String.format("%.6f", cost));
	}

	public CostSummary getSummary() {
		double totalCost = entries.stream().mapToDouble(CostEntry::cost).sum();
		Map<String, Double> costByModel = new ConcurrentHashMap<>();
		Map<String, Double> costByOperation = new ConcurrentHashMap<>();

		for (CostEntry entry : entries) {
			costByModel.merge(entry.model, entry.cost, Double::sum);
			costByOperation.merge(entry.operation, entry.cost, Double::sum);
		}

		return new CostSummary(
				totalCost,
				totalInputTokens.get(),
				totalOutputTokens.get(),
				entries.size(),
				costByModel,
				costByOperation
		);
	}

	public List<CostEntry> getRecentEntries(int limit) {
		int size = entries.size();
		int from = Math.max(0, size - limit);
		return entries.subList(from, size);
	}

	public void reset() {
		entries.clear();
		totalInputTokens.set(0);
		totalOutputTokens.set(0);
	}

	// ── Records ─────────────────────────────────────────────────────────────────

	/** Per-million-token pricing (from OpenAI's published rates). */
	private record ModelPricing(double inputPricePerMillion, double outputPricePerMillion) {}

	public record CostEntry(
			String model,
			String operation,
			int inputTokens,
			int outputTokens,
			double cost,
			long timestamp
	) {}

	public record CostSummary(
			double totalCost,
			long totalInputTokens,
			long totalOutputTokens,
			int totalCalls,
			Map<String, Double> costByModel,
			Map<String, Double> costByOperation
	) {}
}
