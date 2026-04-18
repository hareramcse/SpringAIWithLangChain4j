package com.hs.spring_ai_help_desk.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

@AiService
public interface AIService {

	@SystemMessage(fromResource = "static/helpdesk-system.txt")
	String getResponseFromAssistant(@MemoryId String conversationId, String query);

	@SystemMessage(fromResource = "static/helpdesk-system.txt")
	Flux<String> streamResponseFromAssistant(@MemoryId String conversationId, String query);

}
