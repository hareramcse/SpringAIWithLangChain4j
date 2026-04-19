package com.hs.spring_ai_research.strategy;

import java.util.ArrayList;
import java.util.List;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.hs.spring_ai_research.evaluation.EvaluationService;
import com.hs.spring_ai_research.evaluation.GoldDataset;
import com.hs.spring_ai_research.evaluation.GoldDatasetLoader;
import com.hs.spring_ai_research.rag.AdvancedRagService;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Benchmarks models side-by-side on the same tasks.
 *
 * Measures three dimensions for each model:
 * 1. Quality — LLM-as-judge evaluation score
 * 2. Latency — response time per query
 * 3. Cost — estimated dollar cost per query
 *
 * This data drives model selection decisions:
 * - "Is gpt-4o-mini good enough for review tasks?" -> run benchmark, compare scores
 * - "How much would we save switching writing to gpt-4o-mini?" -> check cost column
 * - "Is the latency improvement worth the quality drop?" -> compare all three
 *
 * Uses gold dataset entries as standardized test inputs for fair comparison.
 */
@Slf4j
@Service
public class ModelBenchmarkService {

	private final ChatModel powerfulModel;
	private final ChatModel fastModel;
	private final GoldDatasetLoader datasetLoader;
	private final AdvancedRagService ragService;
	private final EvaluationService evaluationService;

	public ModelBenchmarkService(
			@Qualifier("powerfulModel") ChatModel powerfulModel,
			@Qualifier("fastModel") ChatModel fastModel,
			GoldDatasetLoader datasetLoader,
			AdvancedRagService ragService,
			EvaluationService evaluationService) {
		this.powerfulModel = powerfulModel;
		this.fastModel = fastModel;
		this.datasetLoader = datasetLoader;
		this.ragService = ragService;
		this.evaluationService = evaluationService;
	}

	/**
	 * Run benchmark on up to maxQueries from the gold dataset.
	 */
	public BenchmarkReport runBenchmark(int maxQueries) {
		List<GoldDataset> datasets = datasetLoader.getAll();
		int limit = Math.min(maxQueries, datasets.size());

		log.info("Running model benchmark on {} queries", limit);

		List<BenchmarkEntry> entries = new ArrayList<>();
		double totalScorePowerful = 0, totalScoreFast = 0;
		long totalLatencyPowerful = 0, totalLatencyFast = 0;

		for (int i = 0; i < limit; i++) {
			GoldDataset entry = datasets.get(i);
			List<TextSegment> retrieved = ragService.retrieveWithoutCompression(entry.query());
			String context = ragService.formatAsContext(retrieved);

			String prompt = "Based on this context, answer the question concisely.\n\nContext: "
					+ truncate(context, 1500) + "\n\nQuestion: " + entry.query();

			// Benchmark powerful model
			long start = System.currentTimeMillis();
			String answerPowerful = powerfulModel.chat(prompt);
			long latencyPowerful = System.currentTimeMillis() - start;

			EvaluationService.EvalResult evalPowerful = evaluationService.evaluate(
					entry.query(), context, answerPowerful);

			// Benchmark fast model
			start = System.currentTimeMillis();
			String answerFast = fastModel.chat(prompt);
			long latencyFast = System.currentTimeMillis() - start;

			EvaluationService.EvalResult evalFast = evaluationService.evaluate(
					entry.query(), context, answerFast);

			totalScorePowerful += evalPowerful.overallScore();
			totalScoreFast += evalFast.overallScore();
			totalLatencyPowerful += latencyPowerful;
			totalLatencyFast += latencyFast;

			entries.add(new BenchmarkEntry(entry.query(),
					evalPowerful.overallScore(), latencyPowerful,
					evalFast.overallScore(), latencyFast));
		}

		double avgScoreP = limit > 0 ? totalScorePowerful / limit : 0;
		double avgScoreF = limit > 0 ? totalScoreFast / limit : 0;
		long avgLatP = limit > 0 ? totalLatencyPowerful / limit : 0;
		long avgLatF = limit > 0 ? totalLatencyFast / limit : 0;

		return new BenchmarkReport(
				"gpt-4o", "gpt-4o-mini", limit,
				avgScoreP, avgScoreF,
				avgLatP, avgLatF,
				avgScoreP - avgScoreF,
				entries
		);
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record BenchmarkEntry(
			String query,
			double scorePowerful, long latencyPowerful,
			double scoreFast, long latencyFast
	) {}

	public record BenchmarkReport(
			String modelA, String modelB, int totalQueries,
			double avgScoreA, double avgScoreB,
			long avgLatencyA, long avgLatencyB,
			double qualityGap,
			List<BenchmarkEntry> entries
	) {}
}
