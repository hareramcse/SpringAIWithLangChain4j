package com.hs.spring_ai_research.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.spring_ai_research.agent.ConversationalResearchService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CONCEPT: Chat Memory / Conversational Follow-ups
 *
 * <p>After the main research pipeline produces a report, users can continue
 * the conversation with follow-up questions. The {@code sessionId} groups messages
 * into a conversation, so the model remembers prior context.</p>
 *
 * <p>Example flow:</p>
 * <ol>
 *   <li>{@code POST /api/research} → produces a report about "transformer architectures"</li>
 *   <li>{@code POST /api/conversation} → sessionId="abc", message="Expand on attention mechanisms"</li>
 *   <li>{@code POST /api/conversation} → sessionId="abc", message="Compare with RNNs"</li>
 * </ol>
 * <p>Each follow-up has full context from previous messages in that session.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {

	private final ConversationalResearchService conversationService;

	@PostMapping
	public ResponseEntity<ConversationResponse> chat(
			@Valid @RequestBody ConversationRequest request) {
		log.info("Conversation request: session={}, message='{}'",
				request.sessionId(), request.message());
		String response = conversationService.chat(request.sessionId(), request.message());
		return ResponseEntity.ok(new ConversationResponse(request.sessionId(), response));
	}

	public record ConversationRequest(
			@NotBlank String sessionId,
			@NotBlank String message
	) {}

	public record ConversationResponse(String sessionId, String response) {}
}
