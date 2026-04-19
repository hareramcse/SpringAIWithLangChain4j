package com.hs.spring_ai_research.dto;

/**
 * Output from the evaluation endpoint ({@code POST /api/eval/run}).
 * All scores are on a 1-10 scale (higher is better).
 *
 * @param question          the evaluated question
 * @param answer            the answer that was scored
 * @param retrievedContext   RAG context used for scoring
 * @param segmentsRetrieved number of chunks retrieved
 * @param faithfulness      is the answer grounded in sources? (1-10)
 * @param relevance         does the answer address the question? (1-10)
 * @param hallucinationFree does the answer avoid fabrication? (1-10)
 * @param overallScore      weighted average of the three scores
 * @param verdict           PASS (>= 7), NEEDS_IMPROVEMENT (5-7), or FAIL (< 5)
 */
public record EvalResponse(
		String question,
		String answer,
		String retrievedContext,
		int segmentsRetrieved,
		int faithfulness,
		int relevance,
		int hallucinationFree,
		double overallScore,
		String verdict
) {}
