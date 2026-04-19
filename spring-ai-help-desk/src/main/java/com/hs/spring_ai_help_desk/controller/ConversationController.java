package com.hs.spring_ai_help_desk.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_help_desk.service.ConversationService;

import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

	private final ConversationService conversationService;

	@GetMapping
	public ResponseEntity<List<String>> getAllConversationIds() {
		return ResponseEntity.ok(conversationService.getAllConversationIds());
	}

	@GetMapping("/{conversationId}")
	public ResponseEntity<List<ChatMessage>> getConversation(@PathVariable String conversationId) {
		List<ChatMessage> messages = conversationService.getConversation(conversationId);
		if (messages.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(messages);
	}

	@DeleteMapping("/{conversationId}")
	public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId) {
		conversationService.deleteConversation(conversationId);
		return ResponseEntity.noContent().build();
	}

}
