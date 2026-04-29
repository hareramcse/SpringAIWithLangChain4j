package com.hs.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Builds MCP {@link Tool} metadata, especially {@code inputSchema} (JSON Schema).
 * <p>
 * The model sees this schema when choosing how to call your tool—clear types and
 * descriptions reduce bad invocations (but always validate again in Java).
 */
public final class McpJsonSchema {

	private McpJsonSchema() {
	}

	public static Tool tool(String name, String description, Map<String, Map<String, Object>> properties,
			List<String> requiredKeys) {
		return Tool.builder()
				.name(name)
				.description(description)
				.inputSchema(objectSchema(properties, requiredKeys))
				.build();
	}

	public static Map<String, Object> stringProperty(String description) {
		return Map.of("type", "string", "description", description);
	}

	public static Map<String, Object> integerProperty(String description, int minimum, int maximum) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "integer");
		schema.put("description", description);
		schema.put("minimum", minimum);
		schema.put("maximum", maximum);
		return schema;
	}

	@SuppressWarnings("unchecked")
	private static McpSchema.JsonSchema objectSchema(Map<String, Map<String, Object>> properties,
			List<String> requiredKeys) {
		return new McpSchema.JsonSchema("object",
				(Map<String, Object>) (Map<?, ?>) properties, requiredKeys, null, null, null);
	}
}
