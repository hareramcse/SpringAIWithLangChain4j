package com.hs.spring_ai_rag.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_rag.service.ChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	@PostMapping("/chat")
	public ResponseEntity<String> chat(@RequestBody String userQuery) {
		return ResponseEntity.ok(chatService.chat(userQuery));
	}
}
