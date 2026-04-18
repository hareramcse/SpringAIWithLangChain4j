package com.hs.spring_ai_adviser.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_adviser.adviser.ChatAdvisor;
import com.hs.spring_ai_adviser.service.ChatService;

import reactor.core.publisher.Flux;

@RestController
public class ChatController {

	@Autowired
	private ChatService chatService;

	@Autowired
	private ChatAdvisor chatAdvisor;

	@PostMapping("/chat")
	public ResponseEntity<String> chat(@RequestBody String query) {
		chatAdvisor.validate(query);
		return ResponseEntity.ok(chatService.chatTemplate(query));
	}

	@PostMapping("/stream-chat")
	public ResponseEntity<Flux<String>> streamChat(@RequestBody String query) {
		chatAdvisor.validate(query);
		return ResponseEntity.ok(chatService.streamChat(query));
	}

}
