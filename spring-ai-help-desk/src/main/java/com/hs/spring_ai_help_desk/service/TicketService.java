package com.hs.spring_ai_help_desk.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hs.spring_ai_help_desk.entity.Ticket;
import com.hs.spring_ai_help_desk.repository.TicketRepository;

import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;

@Service
@Getter
@Setter
public class TicketService {

	@Autowired
	private TicketRepository ticketRepository;

	@Transactional
	public Ticket createTicket(Ticket ticket) {
		ticket.setId(null);
		return ticketRepository.save(ticket);
	}

	@Transactional
	public Ticket updateTicket(Ticket ticket) {
		return ticketRepository.save(ticket);
	}

	public Ticket getTicket(Long ticketId) {
		return ticketRepository.findById(ticketId).orElse(null);
	}

	public Ticket getTicketByEmailId(String username) {
		return ticketRepository.findByEmail(username).orElse(null);
	}

}
