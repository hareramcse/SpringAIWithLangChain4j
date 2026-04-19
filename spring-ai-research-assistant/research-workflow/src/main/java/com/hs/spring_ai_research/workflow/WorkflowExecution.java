package com.hs.spring_ai_research.workflow;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity persisting workflow execution state.
 *
 * By storing the workflow state in the database, we get:
 * - Crash recovery: if the server restarts, resume from last checkpoint
 * - Auditability: see exactly where each workflow is and was
 * - Visibility: admin dashboard showing all active/completed workflows
 *
 * The checkpointData field stores intermediate results (research brief, draft report)
 * so the workflow can resume from any point without re-running earlier steps.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workflow_executions")
public class WorkflowExecution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String question;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WorkflowState currentState;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String stateData;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String checkpointData;

	private int revisionCount;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant updatedAt;

	private Instant completedAt;

	private String finalStatus;
}
