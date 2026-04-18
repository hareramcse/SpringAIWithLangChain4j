package com.hs.spring_ai_help_desk.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hs.spring_ai_help_desk.entity.Ticket;
import com.hs.spring_ai_help_desk.repository.TicketRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketService {

	private final TicketRepository ticketRepository;

	@Transactional
	public Ticket createTicket(Ticket ticket) {
		ticket.setId(null);
		return ticketRepository.save(ticket);
	}

	@Transactional
	public Ticket updateTicket(Ticket ticket) {
		return ticketRepository.save(ticket);
	}

	public Ticket getTicket(UUID ticketId) {
		return ticketRepository.findById(ticketId).orElse(null);
	}

	public Ticket getTicketByEmailId(String email) {
		return ticketRepository.findByEmail(email).orElse(null);
	}

}
