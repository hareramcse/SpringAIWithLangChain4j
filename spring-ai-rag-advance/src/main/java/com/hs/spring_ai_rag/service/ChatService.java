package com.hs.spring_ai_rag.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ChatService {

	String NO_EVIDENCE_REPLY = "Not found in documents";

	@SystemMessage("You answer from the retrieved excerpts only. If there are no excerpts or they do not support an answer, reply with exactly "
			+ NO_EVIDENCE_REPLY + " only.")
	String chat(String userQuery);

}
