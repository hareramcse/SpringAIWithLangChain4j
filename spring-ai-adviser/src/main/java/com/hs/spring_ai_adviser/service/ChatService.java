package com.hs.spring_ai_adviser.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface ChatService {

	@SystemMessage("You are a helpful coding assistant, Explain the concept in detail with example.")
	String chatTemplate(String query);

	@SystemMessage("You are a helpful coding assistant, Explain the concept in detail with example.")
	Flux<String> streamChat(String query);

}
