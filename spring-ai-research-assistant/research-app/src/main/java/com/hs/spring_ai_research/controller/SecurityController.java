package com.hs.spring_ai_research.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.security.AuditLogService;
import com.hs.spring_ai_research.security.ToolUsageGuard;

import lombok.RequiredArgsConstructor;

/**
 * Security and audit endpoints — query audit logs and tool usage statistics.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/security/audit} — recent audit log entries</li>
 *   <li>{@code GET /api/security/audit/{tenantId}} — audit logs for a specific tenant</li>
 *   <li>{@code GET /api/security/audit/summary} — aggregated audit summary</li>
 *   <li>{@code GET /api/security/tool-usage-stats} — tool call statistics</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

	private final AuditLogService auditLogService;
	private final ToolUsageGuard toolUsageGuard;

	@GetMapping("/audit")
	public ResponseEntity<?> getRecentAuditLogs() {
		return ResponseEntity.ok(auditLogService.getRecentLogs());
	}

	@GetMapping("/audit/{tenantId}")
	public ResponseEntity<?> getAuditByTenant(@PathVariable String tenantId) {
		return ResponseEntity.ok(auditLogService.getLogsByTenant(tenantId));
	}

	@GetMapping("/audit/summary")
	public ResponseEntity<?> getAuditSummary() {
		return ResponseEntity.ok(auditLogService.getSummary());
	}

	@GetMapping("/tool-usage-stats")
	public ResponseEntity<?> getToolUsageStats() {
		return ResponseEntity.ok(toolUsageGuard.getStats());
	}
}
