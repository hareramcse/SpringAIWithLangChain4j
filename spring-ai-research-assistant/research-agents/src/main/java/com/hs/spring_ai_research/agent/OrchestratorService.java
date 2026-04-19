package com.hs.spring_ai_research.agent;

import org.springframework.stereotype.Service;

import com.hs.spring_ai_research.agent.ResearcherAgent.ResearchResult;
import com.hs.spring_ai_research.agent.ReviewerAgent.ReviewResult;
import com.hs.spring_ai_research.observability.CostTracker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Supervisor / Orchestrator Pattern
 *
 * <p>This is the "brain" of the research pipeline. It coordinates three specialist agents
 * in sequence, with a quality-driven revision loop and a human approval gate:</p>
 *
 * <pre>
 *   ┌──────────────┐     ┌─────────────┐     ┌────────────────┐
 *   │  Researcher   │────▶│   Writer     │────▶│   Reviewer     │
 *   │  (RAG search) │     │  (report)    │     │  (scoring)     │
 *   └──────────────┘     └──────┬──────┘     └──────┬─────────┘
 *                               │                    │
 *                               │◀── NEEDS_REVISION ─┘  (max 2 revision cycles)
 *                               │
 *                               │    Score below 6.0?
 *                               └───▶ Human Approval Gate
 * </pre>
 *
 * <p>Pipeline outcomes (the {@code status} field in {@link OrchestratorResult}):</p>
 * <ul>
 *   <li>{@code COMPLETED} — reviewer passed the report</li>
 *   <li>{@code PARTIAL} — max revisions reached or reviewer failed</li>
 *   <li>{@code AWAITING_APPROVAL} — sent to human for review (check {@code approvalPipelineId})</li>
 *   <li>{@code NO_DATA} — knowledge base had no relevant documents</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

	/** How many times the Writer can revise before we stop the loop. */
	private static final int MAX_REVISION_CYCLES = 2;

	/** If the Reviewer score is below this and verdict is not PASS, trigger human approval. */
	private static final double HUMAN_REVIEW_THRESHOLD = 6.0;

	private final ResearcherAgent researcherAgent;
	private final WriterAgent writerAgent;
	private final ReviewerAgent reviewerAgent;
	private final CostTracker costTracker;
	private final HumanApprovalGate approvalGate;

	// ── Public API ──────────────────────────────────────────────────────────────

	/**
	 * Runs the full research pipeline for the given question.
	 *
	 * @param question the user's research question (already guardrail-sanitized)
	 * @return result containing the report, review, metadata, or an approval pipeline ID
	 */
	public OrchestratorResult execute(String question) {
		String traceId = costTracker.startTrace("research-pipeline");
		long startTime = System.currentTimeMillis();
		int totalAgentCalls = 0;
		int revisionCycles = 0;

		log.info("=== Orchestrator START: '{}' ===", question);

		// Step 1: Researcher gathers information from the knowledge base via RAG
		log.info("Step 1: Dispatching to Researcher Agent");
		ResearchResult researchResult = researcherAgent.research(question);
		totalAgentCalls++;

		if (researchResult.sources().isEmpty() && researchResult.confidence().equals("NONE")) {
			log.info("Orchestrator: No data found, returning early");
			costTracker.endTrace(traceId);
			return buildResult("NO_DATA", null, null, researchResult,
					totalAgentCalls, 0, startTime, null);
		}

		// Step 2: Writer transforms the research brief into a structured report
		log.info("Step 2: Dispatching to Writer Agent");
		String currentReport = writerAgent.writeReport(researchResult.brief());
		totalAgentCalls++;

		// Step 3: Review loop — Reviewer scores, Writer revises if needed
		ReviewResult reviewResult = null;
		while (revisionCycles <= MAX_REVISION_CYCLES) {
			log.info("Step 3: Dispatching to Reviewer Agent (cycle {})", revisionCycles);
			reviewResult = reviewerAgent.review(question, researchResult.brief(), currentReport);
			totalAgentCalls++;

			if ("PASS".equals(reviewResult.verdict())) {
				log.info("Reviewer PASSED the report (score: {})", reviewResult.overallScore());
				break;
			}
			if ("FAIL".equals(reviewResult.verdict())) {
				log.warn("Reviewer FAILED the report (score: {})", reviewResult.overallScore());
				break;
			}
			if (revisionCycles < MAX_REVISION_CYCLES) {
				log.info("Reviewer requested REVISION (score: {}), sending back to Writer",
						reviewResult.overallScore());
				currentReport = writerAgent.reviseReport(currentReport, reviewResult.reviewJson());
				totalAgentCalls++;
				revisionCycles++;
			} else {
				log.info("Max revision cycles reached, returning best available version");
				break;
			}
		}

		costTracker.endTrace(traceId);

		// Step 4: Human-in-the-Loop — borderline scores trigger human approval
		if (needsHumanApproval(reviewResult)) {
			String pipelineId = approvalGate.submitForApproval(
					question, currentReport, reviewResult.reviewJson(),
					reviewResult.overallScore(),
					"Review score (" + reviewResult.overallScore()
							+ ") below threshold (" + HUMAN_REVIEW_THRESHOLD + ")");
			log.info("Report sent for human approval: pipelineId={}", pipelineId);
			return buildResult("AWAITING_APPROVAL", currentReport,
					reviewResult.reviewJson(), researchResult,
					totalAgentCalls, revisionCycles, startTime, pipelineId);
		}

		String status = reviewResult != null && "PASS".equals(reviewResult.verdict())
				? "COMPLETED" : "PARTIAL";
		log.info("=== Orchestrator END: status={}, agents={}, revisions={}, duration={}ms ===",
				status, totalAgentCalls, revisionCycles,
				System.currentTimeMillis() - startTime);

		return buildResult(status, currentReport,
				reviewResult != null ? reviewResult.reviewJson() : null,
				researchResult, totalAgentCalls, revisionCycles, startTime, null);
	}

	// ── Private helpers ─────────────────────────────────────────────────────────

	private boolean needsHumanApproval(ReviewResult reviewResult) {
		return reviewResult != null
				&& reviewResult.overallScore() > 0
				&& reviewResult.overallScore() < HUMAN_REVIEW_THRESHOLD
				&& !"PASS".equals(reviewResult.verdict());
	}

	private OrchestratorResult buildResult(String status, String report, String review,
										   ResearchResult research, int agentCalls,
										   int revisions, long startTime, String pipelineId) {
		long duration = System.currentTimeMillis() - startTime;
		return new OrchestratorResult(status, report, review, research.brief(),
				new PipelineMetadata(agentCalls, revisions, research.confidence(), duration),
				pipelineId);
	}

	// ── Result records ──────────────────────────────────────────────────────────

	/** Full result returned by the orchestrator pipeline. */
	public record OrchestratorResult(
			/** COMPLETED, PARTIAL, AWAITING_APPROVAL, or NO_DATA */
			String status,
			/** The final report (null if NO_DATA) */
			String report,
			/** The reviewer's JSON assessment (null if skipped) */
			String review,
			/** Raw research brief from the Researcher agent */
			String researchBrief,
			/** Pipeline execution metrics */
			PipelineMetadata metadata,
			/** Non-null only when status is AWAITING_APPROVAL */
			String approvalPipelineId
	) {}

	/** Metrics collected during pipeline execution. */
	public record PipelineMetadata(
			int totalAgentCalls,
			int revisionCycles,
			String researcherConfidence,
			long durationMs
	) {}
}
