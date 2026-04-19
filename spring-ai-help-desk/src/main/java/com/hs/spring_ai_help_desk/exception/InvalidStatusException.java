package com.hs.spring_ai_help_desk.exception;

public class InvalidStatusException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidStatusException(String message) {
		super(message);
	}

}
