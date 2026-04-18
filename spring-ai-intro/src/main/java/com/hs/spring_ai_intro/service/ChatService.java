package com.hs.spring_ai_intro.service;

import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatService {

	String chat(String query);

}
