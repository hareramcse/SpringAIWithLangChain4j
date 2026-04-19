package com.hs.spring_ai_help_desk.exception;

public class TicketNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TicketNotFoundException(String message) {
		super(message);
	}

}
