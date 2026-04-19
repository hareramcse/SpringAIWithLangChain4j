package com.hs.spring_ai_research.constraints;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-request cost ceiling enforcement.
 *
 * Tracks accumulated cost during a request and can abort expensive operations
 * early when the ceiling is about to be exceeded.
 *
 * Example: if a research request costs $0.40 after the research + writing phase,
 * and the ceiling is $0.50, we might skip the revision cycle to stay under budget.
 *
 * This prevents runaway costs from:
 * - Long revision loops
 * - Expensive model calls that could use a cheaper model
 * - Infinite retry cascades
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostCeilingEnforcer {

	private static final Map<String, Double> COST_PER_1K_TOKENS = Map.of(
			"gpt-4o", 0.0125,
			"gpt-4o-mini", 0.000375,
			"text-embedding-3-small", 0.00002
	);

	private final RequestConstraintsConfig config;
	private final ConcurrentHashMap<String, RequestCost> activeCosts = new ConcurrentHashMap<>();
	private final AtomicLong ceilingBreaches = new AtomicLong(0);

	public void startTracking(String requestId) {
		activeCosts.put(requestId, new RequestCost());
	}

	/**
	 * Record cost for a model call within a request.
	 */
	public void recordCost(String requestId, String model, int estimatedTokens) {
		RequestCost cost = activeCosts.get(requestId);
		if (cost == null) return;

		double callCost = estimatedTokens / 1000.0 * COST_PER_1K_TOKENS.getOrDefault(model, 0.001);
		cost.accumulatedCost += callCost;
		cost.callCount++;

		log.debug("Request {} cost update: +${} = ${} total ({} calls)",
				requestId, String.format("%.4f", callCost),
				String.format("%.4f", cost.accumulatedCost), cost.callCount);
	}

	/**
	 * Check if proceeding with another model call would likely exceed the ceiling.
	 * Returns true if it's safe to proceed.
	 */
	public boolean canProceed(String requestId, String model, int estimatedTokens) {
		RequestCost cost = activeCosts.get(requestId);
		if (cost == null) return true;

		double projectedCost = estimatedTokens / 1000.0 * COST_PER_1K_TOKENS.getOrDefault(model, 0.001);
		boolean withinBudget = cost.accumulatedCost + projectedCost <= config.getCostCeilingPerRequest();

		if (!withinBudget) {
			ceilingBreaches.incrementAndGet();
			log.warn("Request {} would exceed cost ceiling: current=${}, projected=+${}, ceiling=${}",
					requestId, String.format("%.4f", cost.accumulatedCost),
					String.format("%.4f", projectedCost),
					String.format("%.2f", config.getCostCeilingPerRequest()));
		}
		return withinBudget;
	}

	public double getCurrentCost(String requestId) {
		RequestCost cost = activeCosts.get(requestId);
		return cost != null ? cost.accumulatedCost : 0;
	}

	public void stopTracking(String requestId) {
		activeCosts.remove(requestId);
	}

	public long getCeilingBreaches() {
		return ceilingBreaches.get();
	}

	private static class RequestCost {
		double accumulatedCost = 0;
		int callCount = 0;
	}
}
