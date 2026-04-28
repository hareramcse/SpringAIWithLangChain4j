package com.hs.spring_ai_rag.safety;

/** Assistant reply failed post–LLM safety validation. */
public final class UnsafeResponseException extends RuntimeException {

	public UnsafeResponseException(String message) {
		super(message);
	}
}
