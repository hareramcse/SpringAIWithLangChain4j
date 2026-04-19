package com.hs.spring_ai_help_desk.service;

import java.time.Year;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
		ticket.setReferenceNumber(generateReferenceNumber());
		return ticketRepository.save(ticket);
	}

	@Transactional
	public Ticket updateTicket(Ticket ticket) {
		return ticketRepository.save(ticket);
	}

	public Ticket getTicket(UUID ticketId) {
		return ticketRepository.findById(ticketId).orElse(null);
	}

	public Ticket getTicketByReferenceNumber(String referenceNumber) {
		return ticketRepository.findByReferenceNumber(referenceNumber).orElse(null);
	}

	public Ticket getTicketByEmailId(String email) {
		return ticketRepository.findTopByEmailOrderByCreatedOnDesc(email).orElse(null);
	}

	public List<Ticket> getAllTicketsByEmail(String email) {
		return ticketRepository.findAllByEmailOrderByCreatedOnDesc(email);
	}

	public Page<Ticket> getAllTickets(Pageable pageable) {
		return ticketRepository.findAll(pageable);
	}

	@Transactional
	public void deleteTicket(UUID ticketId) {
		ticketRepository.deleteById(ticketId);
	}

	private String generateReferenceNumber() {
		long count = ticketRepository.countAllTickets() + 1;
		return String.format("HD-%d-%04d", Year.now().getValue(), count);
	}

}
