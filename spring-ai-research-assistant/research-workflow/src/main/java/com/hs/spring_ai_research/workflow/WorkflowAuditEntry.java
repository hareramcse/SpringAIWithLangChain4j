package com.hs.spring_ai_research.workflow;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full audit trail for every workflow state transition.
 *
 * Records WHO did WHAT, WHEN, and HOW LONG it took.
 * This is essential for:
 * - Debugging: "why did this workflow fail?" -> check the audit trail
 * - Performance: "which step is slowest?" -> aggregate durationMs by agent
 * - Compliance: "prove that human approval was obtained" -> it's in the trail
 * - Cost attribution: token counts per step
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workflow_audit_entries")
public class WorkflowAuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long workflowId;

	@Enumerated(EnumType.STRING)
	private WorkflowState fromState;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WorkflowState toState;

	@Column(nullable = false)
	private Instant timestamp;

	private String agentName;

	private Long durationMs;

	private Integer estimatedTokens;

	@Column(columnDefinition = "TEXT")
	private String notes;
}
