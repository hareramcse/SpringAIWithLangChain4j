package com.hs.spring_ai_research.workflow;

import java.util.Map;
import java.util.Set;

/**
 * Explicit state machine for the research pipeline.
 *
 * Moving from "agents calling each other" to "deterministic state transitions with checkpoints."
 *
 * Each state has a defined set of valid next states. Invalid transitions are rejected.
 * This prevents impossible states (e.g., REVIEWING before WRITING) and makes
 * the workflow auditable and resumable.
 *
 * State machine transitions:
 *   CREATED -> RESEARCHING -> WRITING -> REVIEWING -> COMPLETED
 *                                     -> REVISION -> WRITING (loop)
 *                                     -> PENDING_APPROVAL -> APPROVED -> COMPLETED
 *   Any state -> FAILED
 */
public enum WorkflowState {

	CREATED,
	RESEARCHING,
	WRITING,
	REVIEWING,
	REVISION,
	PENDING_APPROVAL,
	APPROVED,
	COMPLETED,
	FAILED;

	private static final Map<WorkflowState, Set<WorkflowState>> VALID_TRANSITIONS = Map.of(
			CREATED, Set.of(RESEARCHING, FAILED),
			RESEARCHING, Set.of(WRITING, FAILED),
			WRITING, Set.of(REVIEWING, FAILED),
			REVIEWING, Set.of(REVISION, PENDING_APPROVAL, COMPLETED, FAILED),
			REVISION, Set.of(WRITING, FAILED),
			PENDING_APPROVAL, Set.of(APPROVED, FAILED),
			APPROVED, Set.of(COMPLETED, FAILED)
	);

	public boolean canTransitionTo(WorkflowState target) {
		if (this == COMPLETED || this == FAILED) return false;
		Set<WorkflowState> valid = VALID_TRANSITIONS.get(this);
		return valid != null && valid.contains(target);
	}

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}

	public boolean isResumable() {
		return this != COMPLETED && this != FAILED && this != CREATED;
	}
}
