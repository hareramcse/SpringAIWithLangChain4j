package com.hs.spring_ai_prompt.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.input.PromptTemplate;

@Service
public class ChatServiceImpl implements ChatService {

	@Value("classpath:/prompts/user-message.st")
	private Resource userMessageResource;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemMessageResource;

	@Autowired
	private ChatModel chatModel;

	@Override
	public String chat(String query) {
		PromptTemplate promptTemplate = PromptTemplate.from(
				"As an expert in coding and programing. Always write program in java . Now reply for this question: {{query}}");
		String promptText = promptTemplate.apply(Map.of("query", query)).text();
		return chatModel.chat(promptText);
	}

	@Override
	public String userTemplateChat(String query) {
		return chatModel.chat(
				ChatRequest.builder()
						.messages(
								new SystemMessage("As an expert in cricket."),
								new UserMessage(query))
						.build())
				.aiMessage().text();
	}

	@Override
	public String chatTemplate() {
		SystemMessage systemMsg = new SystemMessage(
				"You are a helpful coding assistant. You are an expert in coding.");

		PromptTemplate userTemplate = PromptTemplate.from(
				"What is {{techName}}? tell me also about {{techExample}}");
		String userText = userTemplate.apply(
				Map.of("techName", "Spring", "techExample", "spring exception")).text();

		return chatModel.chat(
				ChatRequest.builder()
						.messages(systemMsg, new UserMessage(userText))
						.build())
				.aiMessage().text();
	}

	@Override
	public String chatResourceTemplate() {
		try {
			String systemText = systemMessageResource.getContentAsString(StandardCharsets.UTF_8);
			String userTemplateText = userMessageResource.getContentAsString(StandardCharsets.UTF_8);

			PromptTemplate userTemplate = PromptTemplate.from(userTemplateText);
			String userText = userTemplate.apply(Map.of("concept", "Python iteration")).text();

			return chatModel.chat(
					ChatRequest.builder()
							.messages(new SystemMessage(systemText), new UserMessage(userText))
							.build())
					.aiMessage().text();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read prompt resources", e);
		}
	}

}
