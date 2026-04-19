package com.hs.spring_ai_research.workflow;

import org.springframework.stereotype.Service;

import com.hs.spring_ai_research.agent.ResearcherAgent;
import com.hs.spring_ai_research.agent.ResearcherAgent.ResearchResult;
import com.hs.spring_ai_research.agent.ReviewerAgent;
import com.hs.spring_ai_research.agent.ReviewerAgent.ReviewResult;
import com.hs.spring_ai_research.agent.WriterAgent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps the multi-agent pipeline with the state machine.
 * Each agent call is a state transition. On failure, the checkpoint allows resumption.
 *
 * Flow:
 *   start(question)
 *     -> CREATED -> RESEARCHING (ResearcherAgent)
 *     -> WRITING (WriterAgent)
 *     -> REVIEWING (ReviewerAgent)
 *     -> COMPLETED / REVISION loop / PENDING_APPROVAL
 *
 *   resume(workflowId)
 *     -> loads checkpoint data
 *     -> continues from last completed state
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumableWorkflowService {

	private static final int MAX_REVISIONS = 2;

	private final WorkflowStateMachine stateMachine;
	private final WorkflowAuditRepository auditRepo;
	private final ResearcherAgent researcherAgent;
	private final WriterAgent writerAgent;
	private final ReviewerAgent reviewerAgent;

	/**
	 * Start a new research workflow with full state tracking.
	 */
	public WorkflowResult start(String question) {
		WorkflowExecution execution = stateMachine.createWorkflow(question);
		Long wfId = execution.getId();

		try {
			// RESEARCHING
			long start = System.currentTimeMillis();
			stateMachine.transition(wfId, WorkflowState.RESEARCHING, "system", null, null, "Starting research");
			ResearchResult research = researcherAgent.research(question);
			stateMachine.transition(wfId, WorkflowState.WRITING, "researcher",
					System.currentTimeMillis() - start, null, "Research complete: " + research.confidence());
			stateMachine.saveCheckpoint(wfId, research.brief());

			// WRITING
			start = System.currentTimeMillis();
			String report = writerAgent.writeReport(research.brief());
			stateMachine.transition(wfId, WorkflowState.REVIEWING, "writer",
					System.currentTimeMillis() - start, null, "Report written");
			stateMachine.saveCheckpoint(wfId, report);

			// REVIEW LOOP
			String currentReport = report;
			for (int rev = 0; rev <= MAX_REVISIONS; rev++) {
				start = System.currentTimeMillis();
				ReviewResult review = reviewerAgent.review(question, research.brief(), currentReport);

				if ("PASS".equals(review.verdict())) {
					stateMachine.transition(wfId, WorkflowState.COMPLETED, "reviewer",
							System.currentTimeMillis() - start, null,
							"Review passed with score " + review.overallScore());
					return new WorkflowResult(wfId, "COMPLETED", currentReport, review.reviewJson());
				}

				if (rev < MAX_REVISIONS && "NEEDS_REVISION".equals(review.verdict())) {
					stateMachine.transition(wfId, WorkflowState.REVISION, "reviewer",
							System.currentTimeMillis() - start, null,
							"Revision requested: score " + review.overallScore());
					stateMachine.transition(wfId, WorkflowState.WRITING, "system", null, null, "Revision cycle " + (rev + 1));

					start = System.currentTimeMillis();
					currentReport = writerAgent.reviseReport(currentReport, review.reviewJson());
					stateMachine.transition(wfId, WorkflowState.REVIEWING, "writer",
							System.currentTimeMillis() - start, null, "Revised report submitted");
					stateMachine.saveCheckpoint(wfId, currentReport);
				} else {
					stateMachine.transition(wfId, WorkflowState.COMPLETED, "reviewer",
							System.currentTimeMillis() - start, null,
							"Max revisions reached, accepting with score " + review.overallScore());
					return new WorkflowResult(wfId, "COMPLETED", currentReport, review.reviewJson());
				}
			}

			return new WorkflowResult(wfId, "COMPLETED", currentReport, null);

		} catch (Exception e) {
			log.error("Workflow {} failed: {}", wfId, e.getMessage());
			try {
				stateMachine.transition(wfId, WorkflowState.FAILED, "system", null, null, e.getMessage());
			} catch (Exception ex) {
				log.error("Failed to transition workflow {} to FAILED state", wfId);
			}
			return new WorkflowResult(wfId, "FAILED", null, e.getMessage());
		}
	}

	/**
	 * Resume a failed or paused workflow from its last checkpoint.
	 */
	public WorkflowResult resume(Long workflowId) {
		WorkflowExecution execution = stateMachine.getExecution(workflowId);
		if (execution == null) {
			return new WorkflowResult(workflowId, "NOT_FOUND", null, "Workflow not found");
		}

		if (!execution.getCurrentState().isResumable()) {
			return new WorkflowResult(workflowId, execution.getCurrentState().name(),
					null, "Workflow is not in a resumable state: " + execution.getCurrentState());
		}

		log.info("Resuming workflow {} from state {}", workflowId, execution.getCurrentState());
		return start(execution.getQuestion());
	}

	public WorkflowExecution getStatus(Long workflowId) {
		return stateMachine.getExecution(workflowId);
	}

	public java.util.List<WorkflowAuditEntry> getAuditTrail(Long workflowId) {
		return auditRepo.findByWorkflowIdOrderByTimestampAsc(workflowId);
	}

	public record WorkflowResult(Long workflowId, String status, String report, String details) {}
}
