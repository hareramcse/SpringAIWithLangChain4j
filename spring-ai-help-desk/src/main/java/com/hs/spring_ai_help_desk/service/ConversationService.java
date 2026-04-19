package com.hs.spring_ai_help_desk.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.hs.spring_ai_help_desk.config.JdbcChatMemoryStore;

import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService {

	private final JdbcChatMemoryStore chatMemoryStore;

	public List<String> getAllConversationIds() {
		return chatMemoryStore.getAllConversationIds();
	}

	public List<ChatMessage> getConversation(String conversationId) {
		return chatMemoryStore.getMessages(conversationId);
	}

	public void deleteConversation(String conversationId) {
		chatMemoryStore.deleteMessages(conversationId);
	}

}
