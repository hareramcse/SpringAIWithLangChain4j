package com.hs.spring_ai_rag.service;

import com.hs.spring_ai_rag.rag.RagGuardrailMessages;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatService {

	@SystemMessage(
			"""
			You are a coding assistant specialized in explaining technical concepts clearly and accurately.
			Answer using only the information in the retrieved knowledge-base excerpts supplied with the user message.
			If there are no retrieved excerpts, or those excerpts do not support an answer, respond with exactly """
					+ RagGuardrailMessages.NO_EVIDENCE_RESPONSE
					+ """
			 and nothing else. Do not use outside knowledge or guess.
			""")
	String chat(String userQuery);

}
