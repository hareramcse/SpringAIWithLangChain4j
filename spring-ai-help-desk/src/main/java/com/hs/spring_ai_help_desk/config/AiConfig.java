package com.hs.spring_ai_help_desk.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AiConfig {

	@Bean
	public ChatMemoryProvider chatMemoryProvider(JdbcChatMemoryStore chatMemoryStore) {
		log.info("ChatMemoryStore bean created. {}", chatMemoryStore.getClass().getName());
		return memoryId -> MessageWindowChatMemory.builder()
				.id(memoryId)
				.maxMessages(15)
				.chatMemoryStore(chatMemoryStore)
				.build();
	}

	@Bean
	public JdbcChatMemoryStore chatMemoryStore(DataSource dataSource) {
		return new JdbcChatMemoryStore(dataSource);
	}

}
