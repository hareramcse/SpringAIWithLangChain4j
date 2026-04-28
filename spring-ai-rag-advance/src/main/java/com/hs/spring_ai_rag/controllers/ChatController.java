package com.hs.spring_ai_rag.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_rag.safety.QueryEntryGuardrail;
import com.hs.spring_ai_rag.safety.ResponsePostGuardrail;
import com.hs.spring_ai_rag.service.ChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;
	private final QueryEntryGuardrail queryEntryGuardrail;
	private final ResponsePostGuardrail responsePostGuardrail;

	@PostMapping(value = "/chat", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> chat(@RequestBody String userQuery) {
		queryEntryGuardrail.validate(userQuery);
		String reply = chatService.chat(userQuery);
		String safeReply = responsePostGuardrail.validate(userQuery, reply);
		return ResponseEntity.ok(safeReply);
	}
}
