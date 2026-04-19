package com.hs.spring_ai_help_desk.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_help_desk.dto.TicketResponse;
import com.hs.spring_ai_help_desk.dto.UpdateStatusRequest;
import com.hs.spring_ai_help_desk.entity.Status;
import com.hs.spring_ai_help_desk.entity.Ticket;
import com.hs.spring_ai_help_desk.exception.InvalidStatusException;
import com.hs.spring_ai_help_desk.exception.TicketNotFoundException;
import com.hs.spring_ai_help_desk.service.TicketService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

	private final TicketService ticketService;

	@GetMapping
	public ResponseEntity<Page<TicketResponse>> getAllTickets(@PageableDefault(size = 20) Pageable pageable) {
		Page<TicketResponse> tickets = ticketService.getAllTickets(pageable).map(TicketResponse::from);
		return ResponseEntity.ok(tickets);
	}

	@GetMapping("/{id}")
	public ResponseEntity<TicketResponse> getTicketById(@PathVariable UUID id) {
		Ticket ticket = ticketService.getTicket(id);
		if (ticket == null) {
			throw new TicketNotFoundException("Ticket not found with ID: " + id);
		}
		return ResponseEntity.ok(TicketResponse.from(ticket));
	}

	@GetMapping("/reference/{referenceNumber}")
	public ResponseEntity<TicketResponse> getTicketByReference(@PathVariable String referenceNumber) {
		Ticket ticket = ticketService.getTicketByReferenceNumber(referenceNumber);
		if (ticket == null) {
			throw new TicketNotFoundException("Ticket not found with reference: " + referenceNumber);
		}
		return ResponseEntity.ok(TicketResponse.from(ticket));
	}

	@GetMapping("/search")
	public ResponseEntity<List<TicketResponse>> getTicketsByEmail(@RequestParam String email) {
		List<TicketResponse> tickets = ticketService.getAllTicketsByEmail(email).stream()
				.map(TicketResponse::from)
				.toList();
		return ResponseEntity.ok(tickets);
	}

	@PutMapping("/{id}/status")
	public ResponseEntity<TicketResponse> updateTicketStatus(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateStatusRequest request) {
		Ticket ticket = ticketService.getTicket(id);
		if (ticket == null) {
			throw new TicketNotFoundException("Ticket not found with ID: " + id);
		}
		try {
			Status newStatus = Status.valueOf(request.getStatus().toUpperCase().trim());
			ticket.setStatus(newStatus);
			Ticket updated = ticketService.updateTicket(ticket);
			return ResponseEntity.ok(TicketResponse.from(updated));
		} catch (IllegalArgumentException e) {
			throw new InvalidStatusException(
					"Invalid status: '" + request.getStatus() + "'. Valid values: OPEN, IN_PROGRESS, RESOLVED, CLOSED");
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteTicket(@PathVariable UUID id) {
		Ticket ticket = ticketService.getTicket(id);
		if (ticket == null) {
			throw new TicketNotFoundException("Ticket not found with ID: " + id);
		}
		ticketService.deleteTicket(id);
		return ResponseEntity.noContent().build();
	}

}
