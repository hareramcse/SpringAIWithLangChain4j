package com.hs.spring_ai_tool.services;

import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatService {

	String chat(String q);

}
