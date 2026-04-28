package com.hs.spring_ai_rag.kb.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class KnowledgeBasePayloadParsingTest {

	@Test
	void parsesClasspathKnowledgeBaseJson() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		try (var in = getClass().getResourceAsStream("/kb/poc-knowledge-base.json")) {
			KnowledgeBasePayload payload = mapper.readValue(in, KnowledgeBasePayload.class);
			assertThat(payload.description()).contains("POC");
			assertThat(payload.documents()).hasSize(5);
			assertThat(payload.documents().getFirst().id()).isEqualTo("returns-policy");
			assertThat(payload.documents().getFirst().toLangChain4jDocument().text()).contains("Returns and refunds");
		}
	}
}
