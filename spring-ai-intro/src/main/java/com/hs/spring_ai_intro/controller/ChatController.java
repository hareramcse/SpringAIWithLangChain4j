package com.hs.spring_ai_intro.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_intro.service.ChatService;

@RestController
public class ChatController {

	@Autowired
	private ChatService chatService;

	@PostMapping("/chat")
	public ResponseEntity<String> chat(@RequestBody String query) {
		var resultResponse = chatService.chat(query);
		return ResponseEntity.ok(resultResponse);
	}

}
