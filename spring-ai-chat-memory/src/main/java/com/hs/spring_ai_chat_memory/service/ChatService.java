package com.hs.spring_ai_chat_memory.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface ChatService {

	@SystemMessage("You are a helpful coding assistant, Explain the concept in detail with example.")
	String chatTemplate(@MemoryId String userId, @UserMessage String query);

	@SystemMessage("You are a helpful coding assistant, Explain the concept in detail with example.")
	Flux<String> streamChat(String query);

}
