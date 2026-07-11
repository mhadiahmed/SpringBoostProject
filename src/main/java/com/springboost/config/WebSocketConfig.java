package com.springboost.config;

import com.springboost.mcp.McpServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for MCP Server
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final McpServer mcpServer;
    
    @Autowired
    public WebSocketConfig(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mcpServer, "/mcp")
                // setAllowedOrigins("*") is rejected at request time: SockJS defaults
                // to allowCredentials (session cookie), and a literal "*" origin can't
                // be combined with credentialed CORS. Patterns are evaluated
                // per-request instead of emitted literally, so this is spec-legal.
                .setAllowedOriginPatterns("*") // In production, restrict this to specific origins
                .withSockJS(); // Enable SockJS fallback
    }
}
