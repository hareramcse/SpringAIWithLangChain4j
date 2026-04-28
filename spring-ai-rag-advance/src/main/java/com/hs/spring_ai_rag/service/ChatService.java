package com.hs.spring_ai_rag.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatService {

	/**
	 * Exact reply the model must emit when there is no usable retrieval; keep in sync with
	 * {@code app.rag.guardrail.no-evidence-message}.
	 */
	String NO_EVIDENCE_REPLY = "Not found in documents";

	@SystemMessage(
			"""
			You are a coding assistant specialized in explaining technical concepts clearly and accurately.
			Answer using only the information in the retrieved knowledge-base excerpts supplied with the user message.
			If there are no retrieved excerpts, or those excerpts do not support an answer, respond with exactly """
					+ NO_EVIDENCE_REPLY
					+ """
			 and nothing else. Do not use outside knowledge or guess.
			""")
	String chat(String userQuery);

}
