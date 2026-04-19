package com.hs.spring_ai_research.evaluation;

import java.util.List;

/**
 * Represents a single gold-standard test case for evaluation.
 *
 * Gold datasets are the foundation of reliable AI evaluation.
 * Each entry contains a known-good query, expected answer, relevant context,
 * and tags for filtering. These are used by regression tests and A/B tests
 * to measure pipeline quality objectively rather than relying on subjective "it seems to work."
 */
public record GoldDataset(
		String query,
		String expectedAnswer,
		String context,
		List<String> tags
) {}
