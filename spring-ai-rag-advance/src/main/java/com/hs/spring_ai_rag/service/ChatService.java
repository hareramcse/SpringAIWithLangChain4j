package com.hs.spring_ai_rag.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatService {

	@SystemMessage("""
			You are a coding assistant specialized in explaining technical concepts clearly and accurately.
			Answer using the information retrieved from the knowledge base.
			If the answer is not available in the context, respond with: "This query is not in my database."
			""")
	String chat(String userQuery);

}
