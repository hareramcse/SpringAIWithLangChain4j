package com.hs.spring_ai_help_desk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

	@NotBlank(message = "Conversation ID is required")
	private String conversationId;

	@NotBlank(message = "Message cannot be empty")
	@Size(max = 5000, message = "Message must not exceed 5000 characters")
	private String message;

}
