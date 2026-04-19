package com.hs.spring_ai_help_desk.tools;

import org.springframework.stereotype.Component;

import com.hs.spring_ai_help_desk.service.KnowledgeBaseService;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBaseTool {

	private final KnowledgeBaseService knowledgeBaseService;

	@Tool("Search the internal knowledge base for troubleshooting steps, company policies, FAQ answers, " +
			"and technical guidance. Use this tool when the user reports a technical issue and you need " +
			"step-by-step instructions to help resolve it, or when they ask about company IT policies.")
	public String searchKnowledgeBase(
			@P("A descriptive search query about the issue or topic, e.g. 'monitor not turning on' or 'VPN connection failed'") String query) {
		log.info("Knowledge base search: {}", query);
		return knowledgeBaseService.search(query, 3);
	}

}
