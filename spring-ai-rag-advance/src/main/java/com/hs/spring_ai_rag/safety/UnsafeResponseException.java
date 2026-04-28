package com.hs.spring_ai_rag.safety;

/** Assistant reply failed post–LLM safety validation. */
public final class UnsafeResponseException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public UnsafeResponseException(String message) {
		super(message);
	}
}
