package com.hs.spring_ai_research.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.strategy.ModelBenchmarkService;
import com.hs.spring_ai_research.strategy.TaskModelRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Model strategy endpoints — benchmarking and task-to-model registry.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/strategy/benchmark} — run gpt-4o vs gpt-4o-mini benchmark</li>
 *   <li>{@code GET  /api/strategy/registry} — view the task-to-model mapping</li>
 *   <li>{@code GET  /api/strategy/cost-comparison} — compare model costs per task type</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class ModelStrategyController {

	private final ModelBenchmarkService benchmarkService;
	private final TaskModelRegistry taskModelRegistry;

	@PostMapping("/benchmark")
	public ResponseEntity<?> runBenchmark(@RequestParam(defaultValue = "3") int maxQueries) {
		log.info("Running model benchmark with {} queries", maxQueries);
		ModelBenchmarkService.BenchmarkReport report = benchmarkService.runBenchmark(maxQueries);
		return ResponseEntity.ok(report);
	}

	@GetMapping("/registry")
	public ResponseEntity<?> getTaskModelRegistry() {
		return ResponseEntity.ok(taskModelRegistry.getTaskModelMap());
	}

	@GetMapping("/cost-comparison")
	public ResponseEntity<?> getCostComparison() {
		return ResponseEntity.ok(taskModelRegistry.getCostComparison());
	}
}
