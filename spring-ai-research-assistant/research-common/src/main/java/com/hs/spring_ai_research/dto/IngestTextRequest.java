package com.hs.spring_ai_research.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input for text ingestion endpoints ({@code POST /api/documents/ingest-text}
 * and {@code POST /api/data/ingest}).
 *
 * @param text     the document text to ingest (min 10 chars)
 * @param source   a human-readable source name (e.g. "company-policies.txt")
 * @param category categorization for metadata filtering (defaults to "general")
 */
public record IngestTextRequest(
		@NotBlank(message = "Text content is required")
		@Size(min = 10, message = "Text must be at least 10 characters")
		String text,

		@NotBlank(message = "Source name is required")
		String source,

		String category
) {
	public IngestTextRequest {
		if (category == null || category.isBlank()) {
			category = "general";
		}
	}
}
