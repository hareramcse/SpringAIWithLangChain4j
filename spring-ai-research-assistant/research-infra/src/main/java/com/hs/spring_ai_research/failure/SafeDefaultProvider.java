package com.hs.spring_ai_research.failure;

import java.util.Map;


import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Provides safe static responses when the LLM is completely unavailable.
 *
 * When degradation reaches OFFLINE level, no LLM calls are made.
 * Instead, topic-matched template responses are returned.
 *
 * These responses:
 * - Clearly state they are NOT AI-generated
 * - Provide general guidance based on the topic
 * - Suggest the user try again later
 * - Never hallucinate or provide specific "facts"
 *
 * This is the ultimate fallback: users still get something useful,
 * and the system never returns a 500 error just because the LLM is down.
 */
@Slf4j
@Service
public class SafeDefaultProvider {

	private static final String DEFAULT_RESPONSE = """
			[NOTICE: This is a pre-generated response. The AI system is temporarily \
			unavailable for live processing.]
			
			We're unable to generate a custom research report at this time. \
			Please try your query again in a few minutes.
			
			In the meantime, you may find relevant information in:
			- Our knowledge base documentation
			- Previously generated reports (check the cache)
			- The original source materials
			
			We apologize for the inconvenience.""";

	private static final Map<String, String> TOPIC_RESPONSES = Map.of(
			"rag", """
					[NOTICE: Pre-generated response — AI system temporarily offline]
					
					Retrieval Augmented Generation (RAG) enhances LLM responses by grounding them in \
					external knowledge. Key components include document chunking, vector embeddings, \
					similarity search, and context-augmented generation.
					
					For detailed information, please try your query again when the system is back online.""",

			"security", """
					[NOTICE: Pre-generated response — AI system temporarily offline]
					
					AI security encompasses prompt injection detection, PII protection, \
					content moderation, and access control. Key practices include input validation, \
					output filtering, and audit logging.
					
					For detailed analysis, please try again when the system is back online.""",

			"agent", """
					[NOTICE: Pre-generated response — AI system temporarily offline]
					
					AI agents use LLMs to plan, reason, and take actions with tools. Common patterns \
					include single-agent, supervisor (multi-agent), and hierarchical architectures. \
					Key concepts: tool calling, memory management, and human-in-the-loop controls.
					
					For a detailed research report, please try again when the system is back online."""
	);

	/**
	 * Get a safe default response matched to the query topic.
	 */
	public String getResponse(String query) {
		log.info("Returning safe default response for query: '{}'", truncate(query, 60));

		String queryLower = query.toLowerCase();
		for (Map.Entry<String, String> entry : TOPIC_RESPONSES.entrySet()) {
			if (queryLower.contains(entry.getKey())) {
				return entry.getValue();
			}
		}

		return DEFAULT_RESPONSE;
	}

	/**
	 * Check if we have a topic-specific response available.
	 */
	public boolean hasTopicResponse(String query) {
		String queryLower = query.toLowerCase();
		return TOPIC_RESPONSES.keySet().stream().anyMatch(queryLower::contains);
	}

	private String truncate(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
	}
}
