package com.hs.spring_ai_help_desk.tools;

import java.time.Instant;
import java.util.List;

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

	@Tool("Create a new help desk ticket in the database. Returns the created ticket or an error message.")
	public String createTicket(
			@P("summary of the ticket") String summary,
			@P("description of the ticket") String description,
			@P("priority: LOW, MEDIUM, HIGH, or URGENT") String priority,
			@P("category of the issue") String category,
			@P("email address of the user") String email) {
		try {
			Priority ticketPriority = Priority.valueOf(priority.toUpperCase().trim());

			Ticket ticket = Ticket.builder()
					.summary(summary)
					.description(description)
					.priority(ticketPriority)
					.category(category)
					.email(email)
					.status(Status.OPEN)
					.build();

			log.debug("Creating ticket: {}", ticket);
			Ticket saved = ticketService.createTicket(ticket);
			return String.format("Ticket created successfully. Reference: %s, ID: %s, Summary: %s, Priority: %s, Status: %s",
					saved.getReferenceNumber(), saved.getId(), saved.getSummary(), saved.getPriority(), saved.getStatus());
		} catch (IllegalArgumentException e) {
			log.error("Invalid priority value: {}", priority, e);
			return "ERROR: Invalid priority '" + priority + "'. Valid values are: LOW, MEDIUM, HIGH, URGENT.";
		} catch (Exception e) {
			log.error("Failed to create ticket", e);
			return "ERROR: Failed to create ticket — " + e.getMessage();
		}
	}

	@Tool("Retrieve a ticket by the user's email address. Returns ticket details or a message if not found.")
	public String getTicketByEmail(@P("email address to search for") String email) {
		Ticket ticket = ticketService.getTicketByEmailId(email);
		if (ticket == null) {
			return "No ticket found for email: " + email;
		}
		return String.format("Ticket found. Reference: %s, Summary: %s, Priority: %s, Status: %s, Category: %s, Created: %s",
				ticket.getReferenceNumber(), ticket.getSummary(), ticket.getPriority(), ticket.getStatus(),
				ticket.getCategory(), ticket.getCreatedOn());
	}

	@Tool("Get all tickets associated with a user's email address.")
	public String getAllTicketsByEmail(@P("email address to search for") String email) {
		List<Ticket> tickets = ticketService.getAllTicketsByEmail(email);
		if (tickets.isEmpty()) {
			return "No tickets found for email: " + email;
		}
		StringBuilder sb = new StringBuilder("Found " + tickets.size() + " ticket(s) for " + email + ":\n");
		for (Ticket t : tickets) {
			sb.append(String.format("- Reference: %s, Summary: %s, Status: %s, Priority: %s, Created: %s\n",
					t.getReferenceNumber(), t.getSummary(), t.getStatus(), t.getPriority(), t.getCreatedOn()));
		}
		return sb.toString();
	}

	@Tool("Update the status of a ticket identified by email. Valid statuses: OPEN, IN_PROGRESS, RESOLVED, CLOSED.")
	public String updateTicketStatus(
			@P("email address associated with the ticket") String email,
			@P("new status: OPEN, IN_PROGRESS, RESOLVED, or CLOSED") String status) {
		try {
			Status newStatus = Status.valueOf(status.toUpperCase().trim());
			Ticket ticket = ticketService.getTicketByEmailId(email);
			if (ticket == null) {
				return "ERROR: No ticket found for email: " + email;
			}
			ticket.setStatus(newStatus);
			Ticket updated = ticketService.updateTicket(ticket);
			return String.format("Ticket %s status updated to %s.", updated.getReferenceNumber(), updated.getStatus());
		} catch (IllegalArgumentException e) {
			return "ERROR: Invalid status '" + status + "'. Valid values are: OPEN, IN_PROGRESS, RESOLVED, CLOSED.";
		}
	}

	@Tool("Get the current system date and time.")
	public String getCurrentTime() {
		return Instant.now().toString();
	}

}
