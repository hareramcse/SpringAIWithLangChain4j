package com.hs.spring_ai_chat_memory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hs.spring_ai_chat_memory.entity.ChatMemoryEntity;

public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, String> {

}
