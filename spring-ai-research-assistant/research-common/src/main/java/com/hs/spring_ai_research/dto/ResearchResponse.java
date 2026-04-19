package com.hs.spring_ai_research.dto;

/**
 * Output from the main research endpoint ({@code POST /api/research}).
 *
 * @param status             COMPLETED, PARTIAL, CACHED, BLOCKED, MODERATED, AWAITING_APPROVAL, or NO_DATA
 * @param report             the final report (null if blocked/no data)
 * @param review             the Reviewer's JSON assessment
 * @param researchBrief      raw research findings from the Researcher agent
 * @param error              non-null only when status is BLOCKED or MODERATED
 * @param metadata           pipeline execution metrics
 * @param approvalPipelineId non-null only when status is AWAITING_APPROVAL
 */
public record ResearchResponse(
		String status,
		String report,
		String review,
		String researchBrief,
		String error,
		Metadata metadata,
		String approvalPipelineId
) {
	public record Metadata(
			String cacheStatus,
			String modelUsed,
			int agentCalls,
			long durationMs
	) {}
}
