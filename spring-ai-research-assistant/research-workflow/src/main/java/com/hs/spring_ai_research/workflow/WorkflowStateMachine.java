package com.hs.spring_ai_research.workflow;

import java.time.Instant;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core state machine: validates transitions, persists state, creates audit entries.
 *
 * Every state change goes through this class. It ensures:
 * 1. The transition is valid (RESEARCHING -> WRITING is ok, RESEARCHING -> COMPLETED is not)
 * 2. The state is persisted to DB (crash recovery)
 * 3. An audit entry is created (traceability)
 * 4. Checkpoint data is saved (resumability)
 *
 * This replaces ad-hoc "if/else" agent coordination with a deterministic,
 * auditable, resumable state machine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowStateMachine {

	private final WorkflowExecutionRepository executionRepo;
	private final WorkflowAuditRepository auditRepo;

	/**
	 * Create a new workflow execution.
	 */
	@Transactional
	public WorkflowExecution createWorkflow(String question) {
		WorkflowExecution execution = WorkflowExecution.builder()
				.question(question)
				.currentState(WorkflowState.CREATED)
				.revisionCount(0)
				.createdAt(Instant.now())
				.updatedAt(Instant.now())
				.build();

		execution = executionRepo.save(execution);

		auditRepo.save(WorkflowAuditEntry.builder()
				.workflowId(execution.getId())
				.toState(WorkflowState.CREATED)
				.timestamp(Instant.now())
				.agentName("system")
				.notes("Workflow created for: " + truncate(question, 100))
				.build());

		log.info("Workflow {} created for: '{}'", execution.getId(), truncate(question, 60));
		return execution;
	}

	/**
	 * Transition to a new state with validation.
	 */
	@Transactional
	public WorkflowExecution transition(Long workflowId, WorkflowState targetState,
										String agentName, Long durationMs, Integer tokens, String notes) {
		WorkflowExecution execution = executionRepo.findById(workflowId)
				.orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

		WorkflowState currentState = execution.getCurrentState();
		if (!currentState.canTransitionTo(targetState)) {
			throw new IllegalStateException(
					"Invalid transition: " + currentState + " -> " + targetState + " for workflow " + workflowId);
		}

		execution.setCurrentState(targetState);
		execution.setUpdatedAt(Instant.now());

		if (targetState == WorkflowState.REVISION) {
			execution.setRevisionCount(execution.getRevisionCount() + 1);
		}

		if (targetState.isTerminal()) {
			execution.setCompletedAt(Instant.now());
			execution.setFinalStatus(targetState.name());
		}

		executionRepo.save(execution);

		auditRepo.save(WorkflowAuditEntry.builder()
				.workflowId(workflowId)
				.fromState(currentState)
				.toState(targetState)
				.timestamp(Instant.now())
				.agentName(agentName)
				.durationMs(durationMs)
				.estimatedTokens(tokens)
				.notes(notes)
				.build());

		log.info("Workflow {} transitioned: {} -> {} (agent: {})",
				workflowId, currentState, targetState, agentName);
		return execution;
	}

	/**
	 * Save checkpoint data so the workflow can resume from this point.
	 */
	@Transactional
	public void saveCheckpoint(Long workflowId, String checkpointData) {
		WorkflowExecution execution = executionRepo.findById(workflowId)
				.orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));
		execution.setCheckpointData(checkpointData);
		executionRepo.save(execution);
		log.debug("Checkpoint saved for workflow {}", workflowId);
	}

	public WorkflowExecution getExecution(Long workflowId) {
		return executionRepo.findById(workflowId).orElse(null);
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}
}
