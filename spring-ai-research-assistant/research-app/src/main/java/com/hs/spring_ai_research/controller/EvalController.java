package com.hs.spring_ai_research.controller;

import java.util.List;
import java.util.Map;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.dto.EvalRequest;
import com.hs.spring_ai_research.dto.EvalResponse;
import com.hs.spring_ai_research.evaluation.ABTestService;
import com.hs.spring_ai_research.evaluation.EvaluationService;
import com.hs.spring_ai_research.evaluation.EvaluationService.EvalResult;
import com.hs.spring_ai_research.evaluation.GoldDatasetLoader;
import com.hs.spring_ai_research.evaluation.RegressionTestRunner;
import com.hs.spring_ai_research.rag.AdvancedRagService;

import dev.langchain4j.data.segment.TextSegment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Evaluation endpoints — scoring, regression testing, and A/B testing.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/eval/run} — evaluate a single question/answer pair</li>
 *   <li>{@code POST /api/eval/regression} — run the full gold dataset suite</li>
 *   <li>{@code POST /api/eval/ab-test/prompts} — compare two prompt templates</li>
 *   <li>{@code POST /api/eval/ab-test/models} — compare gpt-4o vs gpt-4o-mini</li>
 *   <li>{@code GET  /api/eval/gold-datasets} — list loaded test cases</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

	private final AdvancedRagService ragService;
	private final EvaluationService evaluationService;
	private final RegressionTestRunner regressionTestRunner;
	private final ABTestService abTestService;
	private final GoldDatasetLoader goldDatasetLoader;

	// ── Single evaluation ───────────────────────────────────────────────────────

	/** Scores a single question/answer pair for faithfulness, relevance, and hallucination. */
	@PostMapping("/run")
	public ResponseEntity<EvalResponse> runEvaluation(@Valid @RequestBody EvalRequest request) {
		log.info("Running evaluation for: '{}'", request.question());

		List<TextSegment> retrieved = ragService.retrieveWithoutCompression(request.question());
		String context = ragService.formatAsContext(retrieved);

		String answer = request.answer();
		if (answer == null || answer.isBlank()) {
			answer = "No answer provided — evaluation will score against retrieved context only.";
		}

		EvalResult evalResult = evaluationService.evaluate(request.question(), context, answer);

		EvalResponse response = new EvalResponse(
				request.question(), answer, context, retrieved.size(),
				evalResult.faithfulness(), evalResult.relevance(),
				evalResult.hallucinationFree(), evalResult.overallScore(), evalResult.verdict());

		return ResponseEntity.ok(response);
	}

	// ── Regression testing ──────────────────────────────────────────────────────

	/** Runs all gold dataset queries through the pipeline and detects quality regressions. */
	@PostMapping("/regression")
	public ResponseEntity<?> runRegressionTest(@RequestParam(required = false) String tag) {
		log.info("Running regression test suite (tag filter: {})", tag);
		RegressionTestRunner.RegressionReport report = regressionTestRunner.run(tag);
		return ResponseEntity.ok(report);
	}

	/**
	 * Get the last regression test run results.
	 */
	@GetMapping("/regression/last")
	public ResponseEntity<?> getLastRegressionRun() {
		RegressionTestRunner.RegressionReport lastRun = regressionTestRunner.getLastRun();
		if (lastRun == null) {
			return ResponseEntity.ok(Map.of("message", "No regression tests have been run yet"));
		}
		return ResponseEntity.ok(lastRun);
	}

	// ── A/B testing ─────────────────────────────────────────────────────────────

	/** Compares two prompt templates side-by-side on the gold dataset. */
	@PostMapping("/ab-test/prompts")
	public ResponseEntity<?> abTestPrompts(@RequestBody PromptABRequest request) {
		log.info("Running A/B test on prompts");
		ABTestService.ABTestReport report = abTestService.comparePrompts(
				request.promptA(), request.promptB(), request.tag());
		return ResponseEntity.ok(report);
	}

	/** Compares the powerful model (gpt-4o) vs the fast model (gpt-4o-mini) on gold dataset. */
	@PostMapping("/ab-test/models")
	public ResponseEntity<?> abTestModels(@RequestParam(required = false) String tag) {
		log.info("Running A/B test: powerful vs fast model");
		ABTestService.ABTestReport report = abTestService.compareModels(tag);
		return ResponseEntity.ok(report);
	}

	// ── Gold dataset inspection ──────────────────────────────────────────────────

	/** Lists all loaded gold dataset test cases, optionally filtered by tag. */
	@GetMapping("/gold-datasets")
	public ResponseEntity<?> listGoldDatasets(@RequestParam(required = false) String tag) {
		if (tag != null) {
			return ResponseEntity.ok(goldDatasetLoader.getByTag(tag));
		}
		return ResponseEntity.ok(goldDatasetLoader.getAll());
	}

	record PromptABRequest(String promptA, String promptB, String tag) {}
}
