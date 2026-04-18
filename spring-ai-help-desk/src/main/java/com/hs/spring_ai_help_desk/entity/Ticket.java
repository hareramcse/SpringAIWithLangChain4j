package com.hs.spring_ai_help_desk.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "help_desk_tickets")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Ticket implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(length = 500)
	private String summary;

	@Enumerated(EnumType.STRING)
	private Priority priority;

	private String category;

	@Column(length = 1000)
	private String description;

	@Column(unique = true)
	private String email;

	private LocalDateTime createdOn;
	private LocalDateTime updatedOn;

	@Enumerated(EnumType.STRING)
	private Status status;

	@PrePersist
	void preSave() {
		if (this.createdOn == null) {
			this.createdOn = LocalDateTime.now();
		}
		this.updatedOn = LocalDateTime.now();
	}

	@PreUpdate
	void preUpdate() {
		this.updatedOn = LocalDateTime.now();
	}

}
