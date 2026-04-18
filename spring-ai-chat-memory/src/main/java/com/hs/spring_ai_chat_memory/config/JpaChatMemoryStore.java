package com.hs.spring_ai_chat_memory.config;

import java.util.ArrayList;
import java.util.List;

import com.hs.spring_ai_chat_memory.entity.ChatMemoryEntity;
import com.hs.spring_ai_chat_memory.repository.ChatMemoryRepository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public class JpaChatMemoryStore implements ChatMemoryStore {

	private final ChatMemoryRepository repository;

	public JpaChatMemoryStore(ChatMemoryRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<ChatMessage> getMessages(Object memoryId) {
		return repository.findById(memoryId.toString())
				.map(entity -> ChatMessageDeserializer.messagesFromJson(entity.getMessages()))
				.orElseGet(ArrayList::new);
	}

	@Override
	public void updateMessages(Object memoryId, List<ChatMessage> messages) {
		String json = ChatMessageSerializer.messagesToJson(messages);
		ChatMemoryEntity entity = repository.findById(memoryId.toString())
				.orElseGet(() -> {
					ChatMemoryEntity newEntity = new ChatMemoryEntity();
					newEntity.setMemoryId(memoryId.toString());
					return newEntity;
				});
		entity.setMessages(json);
		repository.save(entity);
	}

	@Override
	public void deleteMessages(Object memoryId) {
		repository.deleteById(memoryId.toString());
	}

}
