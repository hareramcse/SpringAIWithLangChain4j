package com.hs.config;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hs.tool.ShoppingCartMcpService;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class McpToolConfig {

	@Bean
	public McpSyncServer mcpServer(ShoppingCartMcpService cartService) {
		StdioServerTransportProvider transport = new StdioServerTransportProvider(
				new JacksonMcpJsonMapper(JsonMapper.builder().build()));

		McpSyncServer server = McpServer.sync(transport)
				.serverInfo("shopping-cart-mcp-server", "1.0.0")
				.capabilities(ServerCapabilities.builder().tools(true).build())
				.build();

		server.addTool(addToCartTool(cartService));
		server.addTool(removeCartTool(cartService));
		server.addTool(getCartsTool(cartService));
		server.addTool(getCartTotalTool(cartService));
		server.addTool(pingTool(cartService));

		log.info("MCP Server started with 5 tools: addToCart, removeCart, getCarts, getCartTotal, ping");
		return server;
	}

	private SyncToolSpecification addToCartTool(ShoppingCartMcpService cartService) {
		return SyncToolSpecification.builder()
				.tool(tool("addToCart",
						"Add a product to the shopping cart. Available products: iPhone, MacBook Air, Boat Airdopes.",
						Map.of("productName", prop("string", "Name of the product"),
								"quantity", prop("integer", "Number of items to add")),
						List.of("productName", "quantity")))
			.callHandler((exchange, request) -> {
				String productName = request.arguments().get("productName").toString();
				int quantity = ((Number) request.arguments().get("quantity")).intValue();
				log.info("[TOOL CALL] addToCart | productName={}, quantity={}", productName, quantity);
				String result = cartService.addToCart(productName, quantity);
				log.info("[TOOL RESULT] addToCart | {}", result);
				return textResult(result);
			})
				.build();
	}

	private SyncToolSpecification removeCartTool(ShoppingCartMcpService cartService) {
		return SyncToolSpecification.builder()
				.tool(tool("removeCart",
						"Remove a product from the shopping cart.",
						Map.of("productName", prop("string", "Product name to remove")),
						List.of("productName")))
			.callHandler((exchange, request) -> {
				String productName = request.arguments().get("productName").toString();
				log.info("[TOOL CALL] removeCart | productName={}", productName);
				String result = cartService.removeCart(productName);
				log.info("[TOOL RESULT] removeCart | {}", result);
				return textResult(result);
			})
				.build();
	}

	private SyncToolSpecification getCartsTool(ShoppingCartMcpService cartService) {
		return SyncToolSpecification.builder()
				.tool(tool("getCarts", "Retrieve the current shopping cart items.", Map.of(), List.of()))
				.callHandler((exchange, request) -> {
				log.info("[TOOL CALL] getCarts");
				String result = cartService.getCarts().toString();
				log.info("[TOOL RESULT] getCarts | {}", result);
				return textResult(result);
			})
				.build();
	}

	private SyncToolSpecification getCartTotalTool(ShoppingCartMcpService cartService) {
		return SyncToolSpecification.builder()
				.tool(tool("getCartTotal", "Calculate the total price of items in the shopping cart.", Map.of(), List.of()))
				.callHandler((exchange, request) -> {
				log.info("[TOOL CALL] getCartTotal");
				double total = cartService.getCartTotal();
				log.info("[TOOL RESULT] getCartTotal | total={}", total);
				return textResult(String.valueOf(total));
			})
				.build();
	}

	private SyncToolSpecification pingTool(ShoppingCartMcpService cartService) {
		return SyncToolSpecification.builder()
				.tool(tool("ping", "Test MCP tool connectivity.", Map.of(), List.of()))
				.callHandler((exchange, request) -> {
				log.info("[TOOL CALL] ping");
				String result = cartService.ping();
				log.info("[TOOL RESULT] ping | {}", result);
				return textResult(result);
			})
				.build();
	}

	// --- Helper methods ---

	private static Tool tool(String name, String description,
			Map<String, Map<String, Object>> properties, List<String> required) {
		return Tool.builder()
				.name(name)
				.description(description)
				.inputSchema(buildSchema(properties, required))
				.build();
	}

	private static Map<String, Object> prop(String type, String description) {
		return Map.of("type", type, "description", description);
	}

	private static CallToolResult textResult(String text) {
		return CallToolResult.builder()
				.content(List.of(new McpSchema.TextContent(text)))
				.build();
	}

	@SuppressWarnings("unchecked")
	private static McpSchema.JsonSchema buildSchema(
			Map<String, Map<String, Object>> properties, List<String> required) {
		return new McpSchema.JsonSchema("object",
				(Map<String, Object>) (Map<?, ?>) properties, required, null, null, null);
	}

}
