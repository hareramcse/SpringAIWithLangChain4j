package com.hs.spring_ai_research.controller;

import java.util.List;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.workflow.ResumableWorkflowService;
import com.hs.spring_ai_research.workflow.WorkflowAuditEntry;
import com.hs.spring_ai_research.workflow.WorkflowExecution;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * State-tracked workflow endpoints — research with checkpoints and audit trail.
 *
 * <p>Unlike {@code /api/research} which runs the pipeline synchronously,
 * workflows persist state to the database so they can be resumed after failures.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code POST /api/workflow/start} — start a new research workflow</li>
 *   <li>{@code POST /api/workflow/{id}/resume} — resume a failed/paused workflow</li>
 *   <li>{@code GET  /api/workflow/{id}/status} — get current workflow state</li>
 *   <li>{@code GET  /api/workflow/{id}/audit} — get the full state transition history</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

	private final ResumableWorkflowService workflowService;

	@PostMapping("/start")
	public ResponseEntity<?> startWorkflow(@RequestBody WorkflowRequest request) {
		log.info("Starting workflow for: '{}'", request.question());
		ResumableWorkflowService.WorkflowResult result = workflowService.start(request.question());
		return ResponseEntity.ok(result);
	}

	@PostMapping("/{id}/resume")
	public ResponseEntity<?> resumeWorkflow(@PathVariable Long id) {
		log.info("Resuming workflow: {}", id);
		ResumableWorkflowService.WorkflowResult result = workflowService.resume(id);
		return ResponseEntity.ok(result);
	}

	@GetMapping("/{id}/status")
	public ResponseEntity<?> getStatus(@PathVariable Long id) {
		WorkflowExecution execution = workflowService.getStatus(id);
		if (execution == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(execution);
	}

	@GetMapping("/{id}/audit")
	public ResponseEntity<?> getAuditTrail(@PathVariable Long id) {
		List<WorkflowAuditEntry> trail = workflowService.getAuditTrail(id);
		return ResponseEntity.ok(trail);
	}

	record WorkflowRequest(@NotBlank String question) {}
}
