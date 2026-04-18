package com.hs.spring_ai_chat_memory.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_memory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryEntity {

	@Id
	private String memoryId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String messages;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

}
