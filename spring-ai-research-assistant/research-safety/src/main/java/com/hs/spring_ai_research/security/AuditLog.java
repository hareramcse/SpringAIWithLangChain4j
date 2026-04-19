package com.hs.spring_ai_research.security;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity for security audit logging.
 *
 * Records: WHO queried WHAT, WHEN, WHICH model was used,
 * WHAT documents were retrieved, and HOW MUCH it cost.
 *
 * This is essential for:
 * - Compliance (GDPR: "who accessed personal data?")
 * - Security forensics ("was there unauthorized access?")
 * - Usage analytics ("what do users ask about most?")
 * - Cost attribution ("which tenant is using the most resources?")
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String tenantId;

	private String userId;

	@Column(nullable = false)
	private String action;

	@Column(columnDefinition = "TEXT")
	private String query;

	private String modelUsed;

	private int documentsRetrieved;

	@Column(columnDefinition = "TEXT")
	private String responseSummary;

	private double estimatedCost;

	@Column(nullable = false)
	private Instant timestamp;

	private long durationMs;

	private String ipAddress;

	private boolean guardrailTriggered;
}
