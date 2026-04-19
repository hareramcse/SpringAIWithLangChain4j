package com.hs.spring_ai_research.dto;

import java.util.List;

/**
 * CONCEPT: Structured Output Extraction
 *
 * Type-safe POJO for the Writer agent's output.
 * Instead of returning a raw JSON string, the @AiService returns this record directly.
 */
public record StructuredReport(
		String title,
		String summary,
		List<ReportSection> sections,
		String conclusion,
		String limitations,
		ReportMetadata metadata
) {
	public record ReportSection(
			String heading,
			String content,
			List<String> citations
	) {}

	public record ReportMetadata(
			String confidence,
			int sourcesUsed
	) {}
}
