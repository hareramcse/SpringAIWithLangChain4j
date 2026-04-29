package com.hs.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hs.tool.ShoppingCartMcpService;
import com.hs.entity.CartItem;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * All shopping-cart MCP tools in one place: each tool is {@code metadata + call handler}.
 * <p>
 * Read top-down: {@link #registerOn(McpSyncServer)} lists tools; each {@code private} method
 * defines one tool’s JSON Schema and the Java code that runs when the model invokes it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShoppingCartMcpTools {

	private final ShoppingCartMcpService cartService;
	private final McpToolResponses responses;

	public void registerOn(McpSyncServer server) {
		server.addTool(addToCart());
		server.addTool(removeFromCart());
		server.addTool(getCartContents());
		server.addTool(getCartTotal());
		server.addTool(suggestItems());
		server.addTool(ping());
		log.info("Registered 6 MCP tools on {}", server.getClass().getSimpleName());
	}

	private SyncToolSpecification addToCart() {
		return SyncToolSpecification.builder()
				.tool(McpJsonSchema.tool("addToCart",
						"Add a product to the shopping cart. Available products: iPhone, MacBook Air, Boat Airdopes.",
						Map.of(
								"productName", McpJsonSchema.stringProperty("Name of the product"),
								"quantity", McpJsonSchema.integerProperty("Number of items to add", 1, 999)),
						List.of("productName", "quantity")))
				.callHandler((exchange, request) -> {
					var args = McpToolArguments.mapOrEmpty(request.arguments());
					String productName = args.get("productName").toString();
					int quantity = ((Number) args.get("quantity")).intValue();
					log.info("[TOOL] addToCart productName={} quantity={}", productName, quantity);
					String result = cartService.addToCart(productName, quantity);
					log.info("[TOOL] addToCart -> {}", result);
					return responses.text(result);
				})
				.build();
	}

	private SyncToolSpecification removeFromCart() {
		return SyncToolSpecification.builder()
				.tool(McpJsonSchema.tool("removeCart",
						"Remove a product line from the shopping cart (by product name).",
						Map.of("productName", McpJsonSchema.stringProperty("Product name to remove")),
						List.of("productName")))
				.callHandler((exchange, request) -> {
					var args = McpToolArguments.mapOrEmpty(request.arguments());
					String productName = args.get("productName").toString();
					log.info("[TOOL] removeCart productName={}", productName);
					String result = cartService.removeCart(productName);
					log.info("[TOOL] removeCart -> {}", result);
					return responses.text(result);
				})
				.build();
	}

	private SyncToolSpecification getCartContents() {
		return SyncToolSpecification.builder()
				.tool(McpJsonSchema.tool("getCarts",
						"List all line items currently in the shopping cart (read-only).",
						Map.of(),
						List.of()))
				.callHandler((exchange, request) -> {
					log.info("[TOOL] getCarts");
					List<CartItem> items = cartService.getCarts();
					log.info("[TOOL] getCarts -> {} lines", items.size());
					return responses.text(items.toString());
				})
				.build();
	}

	private SyncToolSpecification getCartTotal() {
		return SyncToolSpecification.builder()
				.tool(McpJsonSchema.tool("getCartTotal",
						"Return the sum of line totals for the shopping cart (read-only).",
						Map.of(),
						List.of()))
				.callHandler((exchange, request) -> {
					log.info("[TOOL] getCartTotal");
					double total = cartService.getCartTotal();
					log.info("[TOOL] getCartTotal -> {}", total);
					return responses.text(String.valueOf(total));
				})
				.build();
	}

	private SyncToolSpecification suggestItems() {
		return SyncToolSpecification.builder()
				.tool(McpJsonSchema.tool("suggestItems",
						"Suggest catalog products from a free-text query (e.g. phone, laptop, audio). "
								+ "Prioritizes items not already in the cart and substring matches on names. "
								+ "Read-only; does not change the cart.",
						Map.of(
								"query", McpJsonSchema.stringProperty(
										"Search text (case-insensitive). Empty string lists prioritized suggestions."),
								"limit", McpJsonSchema.integerProperty("Max suggestions to return", 1, 20)),
						List.of()))
				.callHandler((exchange, request) -> {
					var args = McpToolArguments.mapOrEmpty(request.arguments());
					String query = McpToolArguments.optionalString(args, "query", "");
					int limit = McpToolArguments.clampedInt(args, "limit", 5, 1, 20);
					log.info("[TOOL] suggestItems query='{}' limit={}", query, limit);
					var body = cartService.suggestItems(query, limit);
					log.info("[TOOL] suggestItems -> {} suggestion(s)", body.suggestions().size());
					return responses.json(body);
				})
				.build();
	}

	private SyncToolSpecification ping() {
		return SyncToolSpecification.builder()
				.tool(McpJsonSchema.tool("ping",
						"Connectivity check: returns a short fixed string if the MCP server is alive.",
						Map.of(),
						List.of()))
				.callHandler((exchange, request) -> {
					log.info("[TOOL] ping");
					return responses.text(cartService.ping());
				})
				.build();
	}
}
