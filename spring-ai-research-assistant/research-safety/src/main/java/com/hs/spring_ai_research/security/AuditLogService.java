package com.hs.spring_ai_research.security;

import java.time.Instant;
import java.util.List;


import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprehensive audit logging for all AI operations.
 *
 * Every query, every retrieval, every model call is logged with full context.
 * Uses JPA persistence for durability (survives restarts).
 *
 * Supports querying by tenant for isolation and compliance:
 * - "Show me all queries from tenant X in the last 24h"
 * - "How many guardrail triggers happened today?"
 * - "What's the total cost for tenant Y this month?"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

	private final AuditLogRepository auditRepo;
	private final TenantContext tenantContext;

	/**
	 * Log a query event.
	 */
	public void logQuery(String query, String modelUsed, int docsRetrieved,
						 String responseSummary, double cost, long durationMs,
						 boolean guardrailTriggered) {
		AuditLog entry = AuditLog.builder()
				.tenantId(tenantContext.getTenant())
				.action("QUERY")
				.query(truncate(query, 500))
				.modelUsed(modelUsed)
				.documentsRetrieved(docsRetrieved)
				.responseSummary(truncate(responseSummary, 200))
				.estimatedCost(cost)
				.timestamp(Instant.now())
				.durationMs(durationMs)
				.guardrailTriggered(guardrailTriggered)
				.build();

		auditRepo.save(entry);
		log.debug("Audit logged: tenant={}, action=QUERY, model={}, cost=${}",
				entry.getTenantId(), modelUsed, String.format("%.4f", cost));
	}

	/**
	 * Log a document ingestion event.
	 */
	public void logIngestion(String source, int chunks) {
		AuditLog entry = AuditLog.builder()
				.tenantId(tenantContext.getTenant())
				.action("INGEST")
				.query("Document ingestion: " + source)
				.documentsRetrieved(chunks)
				.timestamp(Instant.now())
				.build();
		auditRepo.save(entry);
	}

	public List<AuditLog> getRecentLogs() {
		return auditRepo.findTop50ByOrderByTimestampDesc();
	}

	public List<AuditLog> getLogsByTenant(String tenantId) {
		return auditRepo.findByTenantIdOrderByTimestampDesc(tenantId);
	}

	public AuditSummary getSummary() {
		long total = auditRepo.count();
		long guardrailEvents = auditRepo.countByGuardrailTriggered(true);
		return new AuditSummary(total, guardrailEvents);
	}

	private String truncate(String text, int maxLength) {
		if (text == null) return null;
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}

	public record AuditSummary(long totalEvents, long guardrailTriggers) {}
}
