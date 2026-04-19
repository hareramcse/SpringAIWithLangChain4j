package com.hs.spring_ai_research.failure;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles partial failures in multi-step AI pipelines.
 *
 * In a multi-agent pipeline, individual steps can fail:
 * - RAG retrieval returns empty (no relevant docs)
 * - Writer agent times out
 * - Reviewer agent produces unparseable output
 *
 * Without partial failure handling, ONE failed step crashes the ENTIRE pipeline.
 * This handler wraps each step, provides fallback data on failure,
 * and marks the output as degraded — users get a result, just lower quality.
 *
 * Design principle: a degraded response is better than no response.
 */
@Slf4j
@Service
public class PartialFailureHandler {

	/**
	 * Execute a pipeline step with fallback on failure.
	 */
	public <T> StepResult<T> executeStep(String stepName, Supplier<T> step, T fallbackValue) {
		try {
			long start = System.currentTimeMillis();
			T result = step.get();
			long duration = System.currentTimeMillis() - start;
			log.debug("Step '{}' succeeded in {}ms", stepName, duration);
			return new StepResult<>(result, stepName, true, null, duration);
		} catch (Exception e) {
			log.warn("Step '{}' FAILED: {}. Using fallback.", stepName, e.getMessage());
			return new StepResult<>(fallbackValue, stepName, false, e.getMessage(), 0);
		}
	}

	/**
	 * Execute a full pipeline with partial failure tracking.
	 */
	public PipelineResult executePipeline(String pipelineName, List<PipelineStep<?>> steps) {
		log.info("Starting pipeline '{}' with {} steps", pipelineName, steps.size());
		List<StepResult<?>> results = new ArrayList<>();
		boolean anyFailed = false;

		for (PipelineStep<?> step : steps) {
			StepResult<?> result = executeStepInternal(step);
			results.add(result);
			if (!result.succeeded()) {
				anyFailed = true;
			}
		}

		String status = anyFailed ? "DEGRADED" : "COMPLETE";
		log.info("Pipeline '{}' finished: status={}, steps={}/{} succeeded",
				pipelineName, status, results.stream().filter(StepResult::succeeded).count(), results.size());

		return new PipelineResult(pipelineName, status, results);
	}

	@SuppressWarnings("unchecked")
	private <T> StepResult<T> executeStepInternal(PipelineStep<T> step) {
		return executeStep(step.name(), step.action(), step.fallback());
	}

	public record StepResult<T>(T result, String stepName, boolean succeeded, String error, long durationMs) {}

	public record PipelineStep<T>(String name, Supplier<T> action, T fallback) {}

	public record PipelineResult(String pipelineName, String status, List<StepResult<?>> stepResults) {
		public boolean isDegraded() { return "DEGRADED".equals(status); }
		public List<String> getFailedSteps() {
			return stepResults.stream()
					.filter(s -> !s.succeeded())
					.map(StepResult::stepName)
					.toList();
		}
	}
}
