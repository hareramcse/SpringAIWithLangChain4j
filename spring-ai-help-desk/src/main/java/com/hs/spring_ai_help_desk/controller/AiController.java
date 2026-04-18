package com.hs.spring_ai_help_desk.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_help_desk.service.AIService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
public class AiController {

	private final AIService service;

	@PostMapping(value = "/helpdesk")
	public ResponseEntity<String> getResponse(@RequestBody String query,
			@RequestHeader("conversationId") String conversationId) {
		return ResponseEntity.ok(service.getResponseFromAssistant(conversationId, query));
	}

	@PostMapping(value = "/helpdesk-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> streamResponse(@RequestBody String query,
			@RequestHeader("conversationId") String conversationId) {
		return service.streamResponseFromAssistant(conversationId, query);
	}

}
