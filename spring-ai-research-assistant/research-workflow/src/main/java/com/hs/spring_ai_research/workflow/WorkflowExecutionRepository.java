package com.hs.spring_ai_research.workflow;

import java.util.List;


import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

	List<WorkflowExecution> findByCurrentStateNot(WorkflowState state);

	List<WorkflowExecution> findByCurrentStateIn(List<WorkflowState> states);
}
