package com.hs.spring_ai_research.controller;

import java.util.Map;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.cache.SemanticCacheService;
import com.hs.spring_ai_research.cache.SemanticCacheService.CacheStats;
import com.hs.spring_ai_research.observability.CostTracker;
import com.hs.spring_ai_research.observability.CostTracker.CostSummary;
import com.hs.spring_ai_research.observability.TraceInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * Observability and admin endpoints — costs, cache stats, and LLM traces.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/admin/costs} — total cost breakdown by model and operation</li>
 *   <li>{@code GET /api/admin/costs/recent} — last N cost entries</li>
 *   <li>{@code GET /api/admin/cache/stats} — semantic cache hit/miss stats</li>
 *   <li>{@code GET /api/admin/traces} — recent LLM call traces</li>
 *   <li>{@code GET /api/admin/traces/summary} — aggregated trace metrics</li>
 * </ul>
 *
 * <p>In production, these would be behind authentication and authorization.</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

	private final CostTracker costTracker;
	private final SemanticCacheService cacheService;
	private final TraceInterceptor traceInterceptor;

	// ── Cost tracking ───────────────────────────────────────────────────────────

	@GetMapping("/costs")
	public ResponseEntity<CostSummary> getCosts() {
		return ResponseEntity.ok(costTracker.getSummary());
	}

	@GetMapping("/costs/recent")
	public ResponseEntity<?> getRecentCosts(
			@RequestParam(defaultValue = "20") int limit) {
		return ResponseEntity.ok(costTracker.getRecentEntries(limit));
	}

	// ── Cache stats ─────────────────────────────────────────────────────────────

	@GetMapping("/cache/stats")
	public ResponseEntity<CacheStats> getCacheStats() {
		return ResponseEntity.ok(cacheService.getStats());
	}

	@DeleteMapping("/cache")
	public ResponseEntity<?> clearCacheStats() {
		cacheService.clearStats();
		return ResponseEntity.ok(Map.of("message", "Cache stats cleared"));
	}

	// ── LLM traces ──────────────────────────────────────────────────────────────

	@GetMapping("/traces")
	public ResponseEntity<?> getTraces(
			@RequestParam(defaultValue = "20") int limit) {
		return ResponseEntity.ok(traceInterceptor.getRecentTraces(limit));
	}

	@GetMapping("/traces/summary")
	public ResponseEntity<?> getTraceSummary() {
		return ResponseEntity.ok(traceInterceptor.getTraceSummary());
	}

	@DeleteMapping("/costs")
	public ResponseEntity<?> resetCosts() {
		costTracker.reset();
		return ResponseEntity.ok(Map.of("message", "Cost tracker reset"));
	}
}
