package com.hs.spring_ai_help_desk.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

	private String conversationId;
	private String message;
	private Instant timestamp;

	public static ChatResponse of(String conversationId, String message) {
		return ChatResponse.builder()
				.conversationId(conversationId)
				.message(message)
				.timestamp(Instant.now())
				.build();
	}

}
