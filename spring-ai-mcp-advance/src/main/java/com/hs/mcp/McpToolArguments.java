package com.hs.mcp;

import java.util.Map;

/**
 * Reads MCP tool arguments ({@code Map<String, Object>}) in a predictable way.
 * <p>
 * The LLM sends JSON; the SDK turns it into a map. Keys may be missing, and numbers may
 * arrive as {@link Number} or (less often) as strings—so handlers should not cast blindly.
 */
public final class McpToolArguments {

	private McpToolArguments() {
	}

	/** Safe view when the SDK passes {@code null}. */
	public static Map<String, Object> mapOrEmpty(Map<String, Object> raw) {
		return raw != null ? raw : Map.of();
	}

	public static String optionalString(Map<String, Object> args, String key, String defaultValue) {
		Object value = args.get(key);
		return value == null ? defaultValue : value.toString().trim();
	}

	/**
	 * Parses an integer argument and clamps it to {@code [min, max]}. Uses {@code defaultValue}
	 * when the key is absent, and also when the value cannot be parsed as an int.
	 */
	public static int clampedInt(Map<String, Object> args, String key, int defaultValue, int min, int max) {
		Object raw = args.get(key);
		if (raw instanceof Number n) {
			return Math.max(min, Math.min(max, n.intValue()));
		}
		if (raw != null) {
			try {
				return Math.max(min, Math.min(max, Integer.parseInt(raw.toString().trim())));
			} catch (NumberFormatException ignored) {
				// fall through
			}
		}
		return defaultValue;
	}
}
