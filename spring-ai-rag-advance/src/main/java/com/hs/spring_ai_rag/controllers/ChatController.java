package com.hs.spring_ai_rag.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_rag.service.ChatService;

@RestController
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping("/chat")
	public ResponseEntity<String> getResponse(@RequestBody String query) {
		return ResponseEntity.ok(chatService.chat(query));
	}
}
