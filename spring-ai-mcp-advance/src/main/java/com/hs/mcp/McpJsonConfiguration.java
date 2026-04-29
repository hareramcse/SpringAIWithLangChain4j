package com.hs.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared Jackson JSON mapper for MCP wire format and for serializing tool payloads.
 */
@Configuration
public class McpJsonConfiguration {

	@Bean
	JsonMapper mcpJsonMapper() {
		return JsonMapper.builder().build();
	}

	@Bean
	JacksonMcpJsonMapper jacksonMcpJsonMapper(JsonMapper jsonMapper) {
		return new JacksonMcpJsonMapper(jsonMapper);
	}
}
