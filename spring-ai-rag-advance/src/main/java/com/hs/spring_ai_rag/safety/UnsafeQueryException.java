package com.hs.spring_ai_rag.safety;

/** Query failed entry-level safety validation. */
public final class UnsafeQueryException extends RuntimeException {

	public UnsafeQueryException(String message) {
		super(message);
	}
}
