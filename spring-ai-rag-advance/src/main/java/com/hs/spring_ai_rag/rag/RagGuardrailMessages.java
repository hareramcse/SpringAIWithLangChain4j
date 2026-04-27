package com.hs.spring_ai_rag.rag;

/**
 * Fixed response when retrieval / re-ranking confidence is too low. Keep in sync with {@code app.rag.guardrail.no-evidence-message} default in {@code application.yaml}.
 */
public final class RagGuardrailMessages {

	public static final String NO_EVIDENCE_RESPONSE = "Not found in documents";

	private RagGuardrailMessages() {
	}
}
