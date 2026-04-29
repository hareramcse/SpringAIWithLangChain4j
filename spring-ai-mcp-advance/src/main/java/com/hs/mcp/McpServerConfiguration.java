package com.hs.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the MCP <strong>sync</strong> server over <strong>stdio</strong> (typical for local tools).
 * <p>
 * Flow: host process starts this JVM → JSON-RPC messages on stdin/stdout →
 * {@link ShoppingCartMcpTools} registers handlers for each tool name.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpServerConfiguration {

	private final JacksonMcpJsonMapper jacksonMcpJsonMapper;
	private final ShoppingCartMcpTools shoppingCartMcpTools;

	@Bean
	public McpSyncServer mcpSyncServer() {
		var transport = new StdioServerTransportProvider(jacksonMcpJsonMapper);
		McpSyncServer server = McpServer.sync(transport)
				.serverInfo("spring-ai-mcp-advance", "1.0.0")
				.capabilities(ServerCapabilities.builder().tools(true).build())
				.build();
		shoppingCartMcpTools.registerOn(server);
		log.info("MCP sync server listening on stdio (shopping-cart-mcp-server v1.0.0)");
		return server;
	}
}
