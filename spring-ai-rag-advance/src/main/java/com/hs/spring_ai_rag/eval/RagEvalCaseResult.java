package com.hs.spring_ai_rag.eval;

import java.util.List;

public record RagEvalCaseResult(
		String id,
		String query,
		boolean chunkHit,
		boolean answerPass,
		boolean answerEvaluated,
		String actualAnswer,
		List<String> retrievedChunkPreviews,
		String error) {

	public static RagEvalCaseResult error(String id, String query, String error) {
		return new RagEvalCaseResult(id, query, false, false, false, "", List.of(), error);
	}
}
