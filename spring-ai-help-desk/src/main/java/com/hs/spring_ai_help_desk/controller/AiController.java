package com.hs.spring_ai_help_desk.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_help_desk.dto.ChatRequest;
import com.hs.spring_ai_help_desk.dto.ChatResponse;
import com.hs.spring_ai_help_desk.service.AIService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AiController {

	private final AIService service;

	@PostMapping
	public ResponseEntity<ChatResponse> getResponse(@Valid @RequestBody ChatRequest request) {
		String response = service.getResponseFromAssistant(request.getConversationId(), request.getMessage());
		return ResponseEntity.ok(ChatResponse.of(request.getConversationId(), response));
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> streamResponse(@Valid @RequestBody ChatRequest request) {
		return service.streamResponseFromAssistant(request.getConversationId(), request.getMessage());
	}

}
