package com.hs.spring_ai_research.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Chat Memory
 *
 * Configures per-session chat memory so the ConversationalResearchService
 * can maintain conversation context across multiple messages.
 *
 * MessageWindowChatMemory keeps the last N messages in memory.
 * For production, you'd use a persistent store (JDBC, Redis) like
 * the help-desk project does.
 *
 * Here we use in-memory for simplicity since this is a POC.
 * maxMessages=20 keeps context manageable and cost-effective.
 */
@Slf4j
@Configuration
public class ChatMemoryConfig {

	@Bean
	public ChatMemoryProvider chatMemoryProvider() {
		log.info("Initializing in-memory ChatMemoryProvider (maxMessages=20)");
		return memoryId -> MessageWindowChatMemory.builder()
				.id(memoryId)
				.maxMessages(20)
				.build();
	}
}
