package com.hs.spring_ai_research.evaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.hs.spring_ai_research.rag.AdvancedRagService;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * A/B Testing service for comparing prompts and models.
 *
 * Runs the same set of queries against two different configurations (prompt A vs B,
 * or model A vs B) and compares the results. This is how you make data-driven
 * decisions about AI system changes instead of guessing.
 *
 * In production, A/B tests run on live traffic (canary deployment: 5-10% to variant B).
 * This implementation runs offline against the gold dataset for safe experimentation.
 */
@Slf4j
@Service
public class ABTestService {

	private final GoldDatasetLoader datasetLoader;
	private final AdvancedRagService ragService;
	private final EvaluationService evaluationService;
	private final ChatModel powerfulModel;
	private final ChatModel fastModel;

	public ABTestService(
			GoldDatasetLoader datasetLoader,
			AdvancedRagService ragService,
			EvaluationService evaluationService,
			@Qualifier("powerfulModel") ChatModel powerfulModel,
			@Qualifier("fastModel") ChatModel fastModel) {
		this.datasetLoader = datasetLoader;
		this.ragService = ragService;
		this.evaluationService = evaluationService;
		this.powerfulModel = powerfulModel;
		this.fastModel = fastModel;
	}

	/**
	 * Compare two prompt templates against the gold dataset.
	 * Both prompts receive the same context and question.
	 */
	public ABTestReport comparePrompts(String promptTemplateA, String promptTemplateB, String tag) {
		List<GoldDataset> datasets = tag != null ? datasetLoader.getByTag(tag) : datasetLoader.getAll();
		log.info("A/B testing prompts on {} entries", datasets.size());

		List<ABTestCaseResult> results = new ArrayList<>();
		double totalScoreA = 0, totalScoreB = 0;

		for (GoldDataset entry : datasets) {
			List<TextSegment> retrieved = ragService.retrieveWithoutCompression(entry.query());
			String context = ragService.formatAsContext(retrieved);

			String fullPromptA = promptTemplateA.replace("{context}", truncate(context, 1500))
					.replace("{question}", entry.query());
			String fullPromptB = promptTemplateB.replace("{context}", truncate(context, 1500))
					.replace("{question}", entry.query());

			String answerA = fastModel.chat(fullPromptA);
			String answerB = fastModel.chat(fullPromptB);

			EvaluationService.EvalResult evalA = evaluationService.evaluate(entry.query(), context, answerA);
			EvaluationService.EvalResult evalB = evaluationService.evaluate(entry.query(), context, answerB);

			totalScoreA += evalA.overallScore();
			totalScoreB += evalB.overallScore();

			results.add(new ABTestCaseResult(entry.query(),
					answerA, evalA.overallScore(), evalA.verdict(),
					answerB, evalB.overallScore(), evalB.verdict(),
					evalB.overallScore() > evalA.overallScore() ? "B" : "A"));
		}

		double avgA = datasets.isEmpty() ? 0 : totalScoreA / datasets.size();
		double avgB = datasets.isEmpty() ? 0 : totalScoreB / datasets.size();
		String winner = avgB > avgA + 0.3 ? "B" : avgA > avgB + 0.3 ? "A" : "TIE";

		return new ABTestReport("prompt-test", Instant.now(), results.size(),
				avgA, avgB, winner, results);
	}

	/**
	 * Compare powerful model vs fast model on the same queries.
	 * Measures quality gap to help decide if the cost difference is justified.
	 */
	public ABTestReport compareModels(String tag) {
		List<GoldDataset> datasets = tag != null ? datasetLoader.getByTag(tag) : datasetLoader.getAll();
		log.info("A/B testing models (powerful vs fast) on {} entries", datasets.size());

		List<ABTestCaseResult> results = new ArrayList<>();
		double totalScoreA = 0, totalScoreB = 0;

		for (GoldDataset entry : datasets) {
			List<TextSegment> retrieved = ragService.retrieveWithoutCompression(entry.query());
			String context = ragService.formatAsContext(retrieved);

			String prompt = "Based on this context, answer the question concisely.\n\nContext: "
					+ truncate(context, 1500) + "\n\nQuestion: " + entry.query();

			String answerA = powerfulModel.chat(prompt);
			String answerB = fastModel.chat(prompt);

			EvaluationService.EvalResult evalA = evaluationService.evaluate(entry.query(), context, answerA);
			EvaluationService.EvalResult evalB = evaluationService.evaluate(entry.query(), context, answerB);

			totalScoreA += evalA.overallScore();
			totalScoreB += evalB.overallScore();

			results.add(new ABTestCaseResult(entry.query(),
					answerA, evalA.overallScore(), evalA.verdict(),
					answerB, evalB.overallScore(), evalB.verdict(),
					evalB.overallScore() > evalA.overallScore() ? "fast-model" : "powerful-model"));
		}

		double avgA = datasets.isEmpty() ? 0 : totalScoreA / datasets.size();
		double avgB = datasets.isEmpty() ? 0 : totalScoreB / datasets.size();
		String winner = avgB > avgA + 0.3 ? "fast-model"
				: avgA > avgB + 0.3 ? "powerful-model" : "TIE";

		return new ABTestReport("model-comparison", Instant.now(), results.size(),
				avgA, avgB, winner, results);
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record ABTestCaseResult(
			String query,
			String answerA, double scoreA, String verdictA,
			String answerB, double scoreB, String verdictB,
			String winner
	) {}

	public record ABTestReport(
			String testType, Instant timestamp, int totalCases,
			double averageScoreA, double averageScoreB, String winner,
			List<ABTestCaseResult> results
	) {}
}
