package com.hs.spring_ai_help_desk.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_help_desk.service.AIService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1")
public class AiController {

	@Autowired
	private AIService service;

	@PostMapping(value = "/helpdesk")
	public ResponseEntity<String> getResponse(@RequestBody String query,
			@RequestHeader("conversationId") String conversationId) {
		return ResponseEntity.ok(service.getResponseFromAssistant(conversationId, query));
	}

	@PostMapping(value = "/helpdesk-stream")
	public Flux<String> streamResponse(@RequestBody String query,
			@RequestHeader("conversationId") String conversationId) {
		return service.streamResponseFromAssistant(conversationId, query);
	}

}
