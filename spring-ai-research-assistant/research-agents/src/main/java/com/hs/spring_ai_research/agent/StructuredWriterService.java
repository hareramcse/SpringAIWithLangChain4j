package com.hs.spring_ai_research.agent;

import com.hs.spring_ai_research.dto.StructuredReport;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * CONCEPT: Structured Output Extraction via @AiService
 *
 * Returns a StructuredReport POJO directly from the LLM.
 * The framework handles JSON schema injection and response parsing.
 *
 * Uses "powerfulModel" (gpt-4o) for best structured output quality.
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "powerfulModel")
public interface StructuredWriterService {

	@SystemMessage("""
			You are an expert Technical Writer. Transform research findings into
			structured reports. Every claim must cite its source from the research brief.
			Write in clear, professional language.
			""")
	@UserMessage("""
			Transform this research brief into a structured report:

			{{researchBrief}}
			""")
	StructuredReport writeReport(String researchBrief);
}
