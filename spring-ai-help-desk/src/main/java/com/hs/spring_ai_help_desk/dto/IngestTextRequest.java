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
public class IngestTextRequest {

	@NotBlank(message = "Source name is required")
	private String sourceName;

	@NotBlank(message = "Content cannot be empty")
	@Size(max = 50000, message = "Content must not exceed 50000 characters")
	private String content;

}
