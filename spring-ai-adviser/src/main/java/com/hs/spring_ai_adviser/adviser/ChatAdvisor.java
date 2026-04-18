package com.hs.spring_ai_adviser.adviser;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ChatAdvisor implements ChatModelListener {

	private final List<String> blockedWords;

	public ChatAdvisor(@Value("${app.safeguard.blocked-words:}") List<String> blockedWords) {
		this.blockedWords = blockedWords;
		log.info("ChatAdvisor initialized with blocked words: {}", blockedWords);
	}

	public void validate(String query) {
		if (containsBlockedContent(query)) {
			throw new BlockedContentException("Request blocked: query contains restricted content " + blockedWords);
		}
	}

	@Override
	public void onRequest(ChatModelRequestContext requestContext) {
		log.info("Request: {}", requestContext.chatRequest().messages());
	}

	@Override
	public void onResponse(ChatModelResponseContext responseContext) {
		var response = responseContext.chatResponse();
		log.info("Response: {}", response.aiMessage().text());

		if (response.tokenUsage() != null) {
			log.info("Prompt Token: {}", response.tokenUsage().inputTokenCount());
			log.info("Completion Token: {}", response.tokenUsage().outputTokenCount());
			log.info("Total Token consumed: {}", response.tokenUsage().totalTokenCount());
		}
	}

	@Override
	public void onError(ChatModelErrorContext errorContext) {
		log.error("Error during chat model call: {}", errorContext.error().getMessage());
	}

	private boolean containsBlockedContent(String text) {
		if (text == null || blockedWords.isEmpty())
			return false;
		String lower = text.toLowerCase();
		return blockedWords.stream().anyMatch(lower::contains);
	}

	public static class BlockedContentException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public BlockedContentException(String message) {
			super(message);
		}
	}

}
