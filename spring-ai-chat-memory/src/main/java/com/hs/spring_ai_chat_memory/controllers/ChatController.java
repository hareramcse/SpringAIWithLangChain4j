package com.hs.spring_ai_chat_memory.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_chat_memory.service.ChatService;

import reactor.core.publisher.Flux;

@RestController
public class ChatController {

	@Autowired
	private ChatService chatService;

	@PostMapping("/chat")
	public ResponseEntity<String> chat(@RequestBody String query,
			@RequestHeader("userId") String userId) {
		return ResponseEntity.ok(chatService.chatTemplate(userId, query));
	}

	@PostMapping("/stream-chat")
	public ResponseEntity<Flux<String>> streamChat(@RequestBody String query) {
		return ResponseEntity.ok(chatService.streamChat(query));
	}

}
