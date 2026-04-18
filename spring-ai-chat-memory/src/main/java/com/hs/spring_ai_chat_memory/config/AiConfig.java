package com.hs.spring_ai_chat_memory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hs.spring_ai_chat_memory.repository.ChatMemoryRepository;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AiConfig {

	@Bean
	public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore,
			@Value("${app.chat-memory.max-messages:10}") int maxMessages) {
		log.info("ChatMemoryStore: {}, maxMessages: {}", chatMemoryStore.getClass().getSimpleName(), maxMessages);
		return memoryId -> MessageWindowChatMemory.builder()
				.id(memoryId)
				.maxMessages(maxMessages)
				.chatMemoryStore(chatMemoryStore)
				.build();
	}

	@Bean
	public ChatMemoryStore chatMemoryStore(ChatMemoryRepository repository) {
		return new JpaChatMemoryStore(repository);
	}

}
