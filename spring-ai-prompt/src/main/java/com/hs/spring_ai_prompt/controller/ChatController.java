package com.hs.spring_ai_prompt.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_prompt.service.ChatService;

@RestController
public class ChatController {

	@Autowired
	private ChatService chatService;

	@PostMapping("/chat")
	public ResponseEntity<String> chat(@RequestBody String query) {
		var resultResponse = chatService.chat(query);
		return ResponseEntity.ok(resultResponse);
	}

	@PostMapping("/chatResource")
	public ResponseEntity<String> chatResource() {
		var resultResponse = chatService.chatResourceTemplate();
		return ResponseEntity.ok(resultResponse);
	}

}
