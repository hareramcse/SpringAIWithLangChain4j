package com.hs.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds {@link CallToolResult} values returned to the MCP host after each tool call.
 * <p>
 * Most tools here return plain text. Structured tools return JSON in both {@code content}
 * (as text) and {@code structuredContent} (parsed JSON) for clients that support it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolResponses {

	private final JsonMapper jsonMapper;
	private final JacksonMcpJsonMapper mcpJsonMapper;

	public CallToolResult text(String text) {
		return CallToolResult.builder()
				.content(List.of(new McpSchema.TextContent(text)))
				.build();
	}

	public CallToolResult json(Object payload) {
		try {
			String json = jsonMapper.writeValueAsString(payload);
			return CallToolResult.builder()
					.content(List.of(new McpSchema.TextContent(json)))
					.structuredContent(mcpJsonMapper, json)
					.build();
		} catch (Exception e) {
			log.error("Failed to serialize MCP tool result to JSON", e);
			return jsonError("SERIALIZATION_ERROR", "Failed to serialize result: " + e.getMessage());
		}
	}

	public CallToolResult jsonError(String code, String message) {
		Map<String, Object> body = Map.of("ok", false, "code", code, "message", message);
		try {
			String json = jsonMapper.writeValueAsString(body);
			return CallToolResult.builder()
					.isError(true)
					.content(List.of(new McpSchema.TextContent(json)))
					.build();
		} catch (Exception e) {
			return CallToolResult.builder()
					.isError(true)
					.content(List.of(new McpSchema.TextContent("{\"ok\":false,\"code\":\"" + code + "\"}")))
					.build();
		}
	}
}
