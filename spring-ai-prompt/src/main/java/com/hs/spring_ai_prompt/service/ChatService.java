package com.hs.spring_ai_prompt.service;

public interface ChatService {

	String chat(String query);

	String userTemplateChat(String query);

	String chatTemplate();

	String chatResourceTemplate();

}
