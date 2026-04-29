package com.hs.dto;

import java.util.List;

/**
 * JSON-friendly DTOs returned by the {@code suggestItems} MCP tool.
 */
public final class SuggestItemsModels {

	private SuggestItemsModels() {
	}

	public record ItemSuggestion(String name, double unitPrice, boolean inCart, String reason) {
	}

	public record SuggestItemsResponse(List<ItemSuggestion> suggestions, String queryUsed, int limitUsed,
			String note) {
	}
}
