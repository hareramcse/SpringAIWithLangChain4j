package com.hs.spring_ai_help_desk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.hs.spring_ai_help_desk.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	Optional<Ticket> findTopByEmailOrderByCreatedOnDesc(String email);

	List<Ticket> findAllByEmailOrderByCreatedOnDesc(String email);

	Optional<Ticket> findByReferenceNumber(String referenceNumber);

	@Query("SELECT COUNT(t) FROM Ticket t")
	long countAllTickets();

}
