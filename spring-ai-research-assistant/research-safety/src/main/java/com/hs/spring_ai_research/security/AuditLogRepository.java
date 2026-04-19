package com.hs.spring_ai_research.security;

import java.util.List;


import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findByTenantIdOrderByTimestampDesc(String tenantId);

	List<AuditLog> findTop50ByOrderByTimestampDesc();

	long countByGuardrailTriggered(boolean triggered);
}
