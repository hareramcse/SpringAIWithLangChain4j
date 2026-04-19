package com.hs.spring_ai_research.strategy;

import java.util.Map;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Maps task types to optimal model configurations.
 *
 * Instead of one model for everything, different tasks have different needs:
 * - RESEARCH: needs strong reasoning -> gpt-4o (expensive but accurate)
 * - WRITING: needs creativity + coherence -> gpt-4o
 * - REVIEW: needs judgment -> gpt-4o-mini (cheaper, good enough for scoring)
 * - CLASSIFICATION: simple categorization -> gpt-4o-mini (fastest, cheapest)
 * - EXTRACTION: structured output -> gpt-4o-mini (reliable for JSON)
 *
 * The registry also includes cost-per-1K-tokens and documented rationale,
 * making it easy for a team to understand and modify model assignments.
 *
 * In production, this integrates with A/B testing to validate that cheaper models
 * actually perform well enough for their assigned tasks.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.strategy")
public class TaskModelRegistry {

	private Map<String, String> taskModelMap = Map.of(
			"RESEARCH", "gpt-4o",
			"WRITING", "gpt-4o",
			"REVIEW", "gpt-4o-mini",
			"CLASSIFICATION", "gpt-4o-mini",
			"EXTRACTION", "gpt-4o-mini"
	);

	private static final Map<String, ModelProfile> MODEL_PROFILES = Map.of(
			"gpt-4o", new ModelProfile("gpt-4o", 2.50, 10.00, 128000,
					"Best reasoning, highest quality. Use for complex/critical tasks."),
			"gpt-4o-mini", new ModelProfile("gpt-4o-mini", 0.15, 0.60, 128000,
					"Fast, cheap, good for simple tasks. 60% cheaper than gpt-4o.")
	);

	public String getModelForTask(String taskType) {
		return taskModelMap.getOrDefault(taskType.toUpperCase(), "gpt-4o-mini");
	}

	public ModelProfile getProfile(String modelName) {
		return MODEL_PROFILES.getOrDefault(modelName,
				new ModelProfile(modelName, 0, 0, 0, "Unknown model"));
	}

	public Map<String, CostComparison> getCostComparison() {
		var result = new java.util.HashMap<String, CostComparison>();
		for (Map.Entry<String, String> entry : taskModelMap.entrySet()) {
			ModelProfile profile = getProfile(entry.getValue());
			ModelProfile alternative = entry.getValue().equals("gpt-4o")
					? getProfile("gpt-4o-mini") : getProfile("gpt-4o");

			double savingsPercent = profile.inputPer1M() > 0
					? (1 - alternative.inputPer1M() / profile.inputPer1M()) * 100 : 0;

			result.put(entry.getKey(), new CostComparison(
					entry.getValue(), profile.inputPer1M(),
					alternative.name(), alternative.inputPer1M(),
					Math.abs(savingsPercent), profile.rationale()));
		}
		return result;
	}

	public record ModelProfile(String name, double inputPer1M, double outputPer1M,
							   int contextWindow, String rationale) {}

	public record CostComparison(String assignedModel, double assignedCostPer1M,
								 String alternativeModel, double alternativeCostPer1M,
								 double costDifferencePercent, String rationale) {}
}
