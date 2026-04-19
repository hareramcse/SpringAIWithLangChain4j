package com.hs.spring_ai_help_desk.config;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcChatMemoryStore implements ChatMemoryStore {

	private final JdbcTemplate jdbcTemplate;

	public JdbcChatMemoryStore(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		initializeSchema();
	}

	private void initializeSchema() {
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS chat_memory (
					memory_id VARCHAR(255) PRIMARY KEY,
					messages TEXT
				)
				""");
		log.info("Chat memory table initialized.");
	}

	@Override
	public List<ChatMessage> getMessages(Object memoryId) {
		List<ChatMessage> messages = new ArrayList<>();
		try {
			List<String> results = jdbcTemplate.query(
					"SELECT messages FROM chat_memory WHERE memory_id = ?",
					(ResultSet rs, int rowNum) -> rs.getString("messages"),
					memoryId.toString());

			if (!results.isEmpty() && results.getFirst() != null) {
				messages = ChatMessageDeserializer.messagesFromJson(results.getFirst());
			}
		} catch (Exception e) {
			log.warn("Failed to retrieve messages for memoryId {}: {}", memoryId, e.getMessage());
		}
		return messages;
	}

	@Override
	public void updateMessages(Object memoryId, List<ChatMessage> messages) {
		try {
			String json = ChatMessageSerializer.messagesToJson(messages);
			int updated = jdbcTemplate.update(
					"UPDATE chat_memory SET messages = ? WHERE memory_id = ?",
					json, memoryId.toString());
			if (updated == 0) {
				jdbcTemplate.update(
						"INSERT INTO chat_memory (memory_id, messages) VALUES (?, ?)",
						memoryId.toString(), json);
			}
		} catch (Exception e) {
			log.error("Failed to update messages for memoryId {}: {}", memoryId, e.getMessage());
		}
	}

	@Override
	public void deleteMessages(Object memoryId) {
		jdbcTemplate.update("DELETE FROM chat_memory WHERE memory_id = ?", memoryId.toString());
	}

	public List<String> getAllConversationIds() {
		return jdbcTemplate.query(
				"SELECT memory_id FROM chat_memory ORDER BY memory_id",
				(ResultSet rs, int rowNum) -> rs.getString("memory_id"));
	}

	public String getRawMessages(String memoryId) {
		List<String> results = jdbcTemplate.query(
				"SELECT messages FROM chat_memory WHERE memory_id = ?",
				(ResultSet rs, int rowNum) -> rs.getString("messages"),
				memoryId);
		return results.isEmpty() ? null : results.getFirst();
	}

}
