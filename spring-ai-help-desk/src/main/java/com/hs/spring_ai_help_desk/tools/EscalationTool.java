package com.hs.spring_ai_help_desk.tools;

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
public class EscalationTool {

	private final TicketService ticketService;

	@Tool("Escalate a ticket to urgent priority and notify the on-call team. Use when the issue is critical, " +
			"the user has been waiting too long, or manual intervention by a senior engineer is required.")
	public String escalateTicket(
			@P("email address associated with the ticket") String email,
			@P("reason for escalation") String reason) {
		Ticket ticket = ticketService.getTicketByEmailId(email);
		if (ticket == null) {
			return "ERROR: No ticket found for email: " + email + ". Please create a ticket first.";
		}

		Priority previousPriority = ticket.getPriority();
		ticket.setPriority(Priority.URGENT);
		ticket.setStatus(Status.IN_PROGRESS);
		ticketService.updateTicket(ticket);

		log.warn("ESCALATION: Ticket {} escalated from {} to URGENT. Reason: {}",
				ticket.getReferenceNumber(), previousPriority, reason);

		return String.format("Ticket %s has been escalated to URGENT priority and marked as IN_PROGRESS. " +
						"Previous priority was %s. Reason: %s. The on-call engineering team has been notified.",
				ticket.getReferenceNumber(), previousPriority, reason);
	}

}
