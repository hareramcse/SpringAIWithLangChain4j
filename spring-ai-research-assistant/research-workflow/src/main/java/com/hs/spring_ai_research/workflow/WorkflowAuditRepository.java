package com.hs.spring_ai_research.workflow;

import java.util.List;


import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowAuditRepository extends JpaRepository<WorkflowAuditEntry, Long> {

	List<WorkflowAuditEntry> findByWorkflowIdOrderByTimestampAsc(Long workflowId);
}
