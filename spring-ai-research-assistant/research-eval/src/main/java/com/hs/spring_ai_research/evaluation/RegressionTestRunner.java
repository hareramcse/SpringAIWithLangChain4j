package com.hs.spring_ai_research.evaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.hs.spring_ai_research.rag.AdvancedRagService;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Regression Testing Pipeline for AI systems.
 *
 * Runs the full gold dataset through the RAG pipeline and evaluates each entry.
 * Compares current results against the previous run to detect regressions.
 *
 * This is the AI equivalent of unit tests — it tells you "did my prompt change
 * break anything?" before you deploy. In production, this runs as part of CI/CD.
 *
 * Key capabilities:
 * - Run all gold dataset entries through the pipeline
 * - Compute per-query scores (faithfulness, correctness, retrieval quality)
 * - Detect regressions by comparing against the last stored run
 * - Aggregate results into a pass/fail summary
 */
@Slf4j
@Service
public class RegressionTestRunner {

	private final GoldDatasetLoader datasetLoader;
	private final AdvancedRagService ragService;
	private final EvaluationService evaluationService;
	private final EvalMetrics evalMetrics;
	private final ChatModel fastModel;

	private final Map<String, RegressionReport> runHistory = new ConcurrentHashMap<>();
	private volatile RegressionReport lastRun;

	public RegressionTestRunner(
			GoldDatasetLoader datasetLoader,
			AdvancedRagService ragService,
			EvaluationService evaluationService,
			EvalMetrics evalMetrics,
			@Qualifier("fastModel") ChatModel fastModel) {
		this.datasetLoader = datasetLoader;
		this.ragService = ragService;
		this.evaluationService = evaluationService;
		this.evalMetrics = evalMetrics;
		this.fastModel = fastModel;
	}

	/**
	 * Run regression test on all gold dataset entries (or filtered by tag).
	 */
	public RegressionReport run(String filterTag) {
		List<GoldDataset> datasets = filterTag != null && !filterTag.isBlank()
				? datasetLoader.getByTag(filterTag)
				: datasetLoader.getAll();

		if (datasets.isEmpty()) {
			return new RegressionReport("no-data", Instant.now(), List.of(),
					0, 0, 0, List.of(), "NO_DATA");
		}

		String runId = "run-" + System.currentTimeMillis();
		log.info("Starting regression test run '{}' with {} entries", runId, datasets.size());

		List<TestCaseResult> results = new ArrayList<>();
		int passed = 0;
		int regressions = 0;
		List<String> regressionDetails = new ArrayList<>();

		for (int i = 0; i < datasets.size(); i++) {
			GoldDataset entry = datasets.get(i);
			log.info("Running test case {}/{}: '{}'", i + 1, datasets.size(),
					truncate(entry.query(), 60));

			TestCaseResult result = runSingleTestCase(entry);
			results.add(result);

			if (result.passed()) {
				passed++;
			}

			if (lastRun != null) {
				TestCaseResult previousResult = findPreviousResult(lastRun, entry.query());
				if (previousResult != null && previousResult.overallScore() > result.overallScore() + 0.5) {
					regressions++;
					regressionDetails.add(String.format("'%s': score dropped %.1f -> %.1f",
							truncate(entry.query(), 40),
							previousResult.overallScore(), result.overallScore()));
				}
			}
		}

		String verdict = regressions > 0 ? "REGRESSION_DETECTED"
				: passed == datasets.size() ? "ALL_PASSED"
				: "PARTIAL_PASS";

		RegressionReport report = new RegressionReport(
				runId, Instant.now(), results, passed,
				datasets.size() - passed, regressions,
				regressionDetails, verdict
		);

		lastRun = report;
		runHistory.put(runId, report);

		log.info("Regression test complete: {}/{} passed, {} regressions, verdict={}",
				passed, datasets.size(), regressions, verdict);
		return report;
	}

	private TestCaseResult runSingleTestCase(GoldDataset entry) {
		try {
			List<TextSegment> retrieved = ragService.retrieveWithoutCompression(entry.query());
			String context = ragService.formatAsContext(retrieved);

			String generatedAnswer = fastModel.chat(
					"Based on this context, answer the question concisely.\n\nContext: "
							+ truncate(context, 1500) + "\n\nQuestion: " + entry.query());

			EvaluationService.EvalResult evalResult = evaluationService.evaluate(
					entry.query(), context, generatedAnswer);

			EvalMetrics.MetricsResult metricsResult = evalMetrics.computeAll(
					generatedAnswer, entry.expectedAnswer(), retrieved, entry.context());

			double overallScore = (evalResult.overallScore() / 10.0 * 0.5) + (metricsResult.compositeScore() * 0.5);
			boolean passed = overallScore >= 0.6;

			return new TestCaseResult(entry.query(), generatedAnswer, retrieved.size(),
					evalResult.faithfulness(), evalResult.relevance(), evalResult.hallucinationFree(),
					metricsResult.correctness(), metricsResult.retrievalPrecision(),
					metricsResult.retrievalRecall(), metricsResult.completeness(),
					overallScore, passed, null);
		} catch (Exception e) {
			log.error("Test case failed for '{}': {}", entry.query(), e.getMessage());
			return new TestCaseResult(entry.query(), null, 0,
					0, 0, 0, 0, 0, 0, 0, 0, false, e.getMessage());
		}
	}

	private TestCaseResult findPreviousResult(RegressionReport previous, String query) {
		return previous.results().stream()
				.filter(r -> r.query().equals(query))
				.findFirst().orElse(null);
	}

	public RegressionReport getLastRun() {
		return lastRun;
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record TestCaseResult(
			String query, String generatedAnswer, int segmentsRetrieved,
			int faithfulness, int relevance, int hallucinationFree,
			double correctness, double retrievalPrecision, double retrievalRecall,
			int completeness, double overallScore, boolean passed, String error
	) {}

	public record RegressionReport(
			String runId, Instant timestamp, List<TestCaseResult> results,
			int totalPassed, int totalFailed, int regressionsDetected,
			List<String> regressionDetails, String verdict
	) {}
}
