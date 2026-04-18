package com.hs.spring_ai_help_desk.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hs.spring_ai_help_desk.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
	Optional<Ticket> findByEmail(String email);
}
