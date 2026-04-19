package com.hs.spring_ai_research.guardrails;

/**
 * Result of a guardrail check. If blocked=true, the request should be rejected.
 */
public record GuardrailResult(
		boolean blocked,
		String type,
		String reason
) {
	public static GuardrailResult safe() {
		return new GuardrailResult(false, null, null);
	}
}
