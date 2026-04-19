package com.hs.spring_ai_research.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

/**
 * CONCEPT: Chat Memory / Conversational Follow-ups
 *
 * This @AiService maintains conversation history per session using @MemoryId.
 * After the main research pipeline produces a report, users can ask follow-up
 * questions like:
 * - "Can you expand on section 2?"
 * - "Compare this with alternative approaches"
 * - "Summarize the key findings in 3 bullet points"
 *
 * The @MemoryId ensures each user session has its own conversation history,
 * so follow-ups have the full context of the research discussion.
 *
 * Without chat memory, each call would be stateless and the model wouldn't
 * know what "section 2" or "this" refers to.
 *
 * Uses "fastModel" (gpt-4o-mini) for cost-effective conversational chat.
 */
@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "fastModel",
		chatMemoryProvider = "chatMemoryProvider")
public interface ConversationalResearchService {

	@SystemMessage("""
			You are a research assistant that helps users explore and discuss research findings.
			You have context from a previous research report. Help the user by:
			- Answering follow-up questions about the research
			- Expanding on specific sections when asked
			- Providing summaries or comparisons
			- Suggesting related topics to explore

			Be concise and cite sources when possible.
			""")
	String chat(@MemoryId String sessionId, @UserMessage String message);
}
