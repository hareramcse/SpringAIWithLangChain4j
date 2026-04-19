package com.hs.spring_ai_research.agent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Human-in-the-Loop (HITL)
 *
 * In production AI systems, not every decision should be fully automated.
 * Human-in-the-loop patterns add approval gates at critical points where:
 * - Stakes are high (e.g., publishing a report, sending an email)
 * - Confidence is low (e.g., reviewer scored the report poorly)
 * - Regulatory compliance requires human oversight
 *
 * This service implements a simple approval gate:
 * 1. Pipeline pauses and creates a "pending approval" request
 * 2. A human reviews the content via an API endpoint
 * 3. Human approves or rejects with optional feedback
 * 4. Pipeline resumes based on the decision
 *
 * The pipeline ID is returned to the caller so they can poll or
 * approve/reject asynchronously.
 */
@Slf4j
@Service
public class HumanApprovalGate {

	private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<>();

	/**
	 * Submit content for human approval. Returns a pipeline ID.
	 */
	public String submitForApproval(String question, String report, String review,
									double reviewScore, String reason) {
		String pipelineId = UUID.randomUUID().toString().substring(0, 12);

		PendingApproval pending = new PendingApproval(
				pipelineId, question, report, review, reviewScore, reason,
				ApprovalStatus.PENDING, null, System.currentTimeMillis()
		);
		pendingApprovals.put(pipelineId, pending);

		log.info("Submitted for human approval: pipelineId={}, reason={}", pipelineId, reason);
		return pipelineId;
	}

	/**
	 * Human approves or rejects the pending report.
	 */
	public PendingApproval decide(String pipelineId, boolean approved, String feedback) {
		PendingApproval pending = pendingApprovals.get(pipelineId);
		if (pending == null) {
			return null;
		}

		PendingApproval updated = new PendingApproval(
				pending.pipelineId(), pending.question(), pending.report(),
				pending.review(), pending.reviewScore(), pending.reason(),
				approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED,
				feedback, pending.createdAt()
		);
		pendingApprovals.put(pipelineId, updated);

		log.info("Human decision for {}: {} (feedback: {})",
				pipelineId, updated.status(), feedback);
		return updated;
	}

	public PendingApproval getStatus(String pipelineId) {
		return pendingApprovals.get(pipelineId);
	}

	public Map<String, PendingApproval> getAllPending() {
		return Map.copyOf(pendingApprovals);
	}

	public enum ApprovalStatus {
		PENDING, APPROVED, REJECTED
	}

	public record PendingApproval(
			String pipelineId,
			String question,
			String report,
			String review,
			double reviewScore,
			String reason,
			ApprovalStatus status,
			String humanFeedback,
			long createdAt
	) {}
}
