package com.hs.spring_ai_help_desk.tools;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.hs.spring_ai_help_desk.entity.Priority;
import com.hs.spring_ai_help_desk.entity.Status;
import com.hs.spring_ai_help_desk.entity.Ticket;
import com.hs.spring_ai_help_desk.service.TicketService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketDatabaseTool {

	private final TicketService ticketService;

	@Tool("This tool helps to create new ticket in database.")
	public Ticket createTicket(
			@P("summary of the ticket") String summary,
			@P("description of the ticket") String description,
			@P("priority: LOW, MEDIUM, HIGH, or URGENT") String priority,
			@P("category of the issue") String category,
			@P("email address of the user") String email) {
		try {
			Ticket ticket = Ticket.builder()
					.summary(summary)
					.description(description)
					.priority(Priority.valueOf(priority))
					.category(category)
					.email(email)
					.status(Status.OPEN)
					.build();

			log.debug("Creating ticket: {}", ticket);
			return ticketService.createTicket(ticket);
		} catch (Exception e) {
			log.error("Failed to create ticket", e);
			return null;
		}
	}

	@Tool("This tool helps to get ticket by email id.")
	public Ticket getTicketByEmail(@P("email id whose ticket is required") String email) {
		return ticketService.getTicketByEmailId(email);
	}

	@Tool("This tool helps to update ticket status.")
	public Ticket updateTicketStatus(
			@P("email id of the ticket to update") String email,
			@P("new status: OPEN, CLOSED, or RESOLVED") String status) {
		Ticket ticket = ticketService.getTicketByEmailId(email);
		if (ticket != null) {
			ticket.setStatus(Status.valueOf(status));
			return ticketService.updateTicket(ticket);
		}
		return null;
	}

	@Tool("This tool helps to get current system time.")
	public String getCurrentTime() {
		return Instant.now().toString();
	}

}
