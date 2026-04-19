package com.hs.spring_ai_help_desk.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hs.spring_ai_help_desk.entity.Priority;
import com.hs.spring_ai_help_desk.entity.Status;
import com.hs.spring_ai_help_desk.entity.Ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

	private UUID id;
	private String referenceNumber;
	private String summary;
	private String description;
	private Priority priority;
	private String category;
	private String email;
	private Status status;
	private LocalDateTime createdOn;
	private LocalDateTime updatedOn;

	public static TicketResponse from(Ticket ticket) {
		return TicketResponse.builder()
				.id(ticket.getId())
				.referenceNumber(ticket.getReferenceNumber())
				.summary(ticket.getSummary())
				.description(ticket.getDescription())
				.priority(ticket.getPriority())
				.category(ticket.getCategory())
				.email(ticket.getEmail())
				.status(ticket.getStatus())
				.createdOn(ticket.getCreatedOn())
				.updatedOn(ticket.getUpdatedOn())
				.build();
	}

}
