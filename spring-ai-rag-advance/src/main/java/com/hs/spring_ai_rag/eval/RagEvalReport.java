package com.hs.spring_ai_rag.eval;

import java.util.List;

public record RagEvalReport(
		int totalCases,
		long chunkHits,
		long answerPasses,
		long answerEvaluatedCount,
		double chunkHitRate,
		double answerPassRate,
		List<RagEvalCaseResult> results) {
}
