package com.hs.spring_ai_research.controller;

import java.util.Map;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.agent.HumanApprovalGate;
import com.hs.spring_ai_research.agent.HumanApprovalGate.PendingApproval;

import lombok.RequiredArgsConstructor;

/**
 * CONCEPT: Human-in-the-Loop (HITL)
 *
 * <p>REST endpoints for the human approval workflow. When the Orchestrator produces
 * a borderline-quality report, it pauses for human review.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET  /api/approvals} — list all pending approvals</li>
 *   <li>{@code GET  /api/approvals/{id}} — view a specific approval request</li>
 *   <li>{@code POST /api/approvals/{id}/decide} — approve or reject with feedback</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

	private final HumanApprovalGate approvalGate;

	@GetMapping
	public ResponseEntity<Map<String, PendingApproval>> getAllPending() {
		return ResponseEntity.ok(approvalGate.getAllPending());
	}

	@GetMapping("/{pipelineId}")
	public ResponseEntity<?> getStatus(@PathVariable String pipelineId) {
		PendingApproval approval = approvalGate.getStatus(pipelineId);
		if (approval == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(approval);
	}

	@PostMapping("/{pipelineId}/decide")
	public ResponseEntity<?> decide(
			@PathVariable String pipelineId,
			@RequestBody DecisionRequest request) {
		PendingApproval result = approvalGate.decide(
				pipelineId, request.approved(), request.feedback());
		if (result == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(result);
	}

	public record DecisionRequest(boolean approved, String feedback) {}
}
