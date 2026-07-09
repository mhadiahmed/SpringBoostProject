package com.springboost.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboost.config.SpringBoostProperties;
import com.springboost.mcp.protocol.McpError;
import com.springboost.mcp.protocol.McpMessage;
import com.springboost.mcp.tools.McpToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP (Model Context Protocol) Server implementation
 * Handles WebSocket connections and tool execution requests from AI assistants
 */
@Slf4j
@Component
public class McpServer implements WebSocketHandler {
    
    private final McpToolRegistry toolRegistry;
    private final SpringBoostProperties properties;
    private final ObjectMapper objectMapper;
    private final McpMessageProcessor messageProcessor;
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    private final AtomicLong messageIdCounter = new AtomicLong(0);

    @Autowired
    public McpServer(McpToolRegistry toolRegistry,
                     SpringBoostProperties properties,
                     ObjectMapper objectMapper,
                     McpMessageProcessor messageProcessor) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.messageProcessor = messageProcessor;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (properties.getMcp().isEnabled()) {
            log.info("MCP Server ready on {}:{}", 
                    properties.getMcp().getHost(), 
                    properties.getMcp().getPort());
            log.info("Available tools: {}", toolRegistry.getToolNames());
        }
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);
        log.info("MCP client connected: {}", sessionId);
        
        // Send welcome message with server capabilities
        sendWelcomeMessage(session);
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage) {
            handleTextMessage(session, (TextMessage) message);
        } else {
            log.warn("Received unsupported message type: {}", message.getClass().getSimpleName());
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error in session {}: {}", session.getId(), exception.getMessage(), exception);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        log.info("MCP client disconnected: {} ({})", sessionId, closeStatus);
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    /**
     * Handle incoming text messages (JSON-RPC requests)
     */
    private void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.debug("Received message: {}", payload);
            
            McpMessage mcpMessage = objectMapper.readValue(payload, McpMessage.class);
            McpMessage response = messageProcessor.process(mcpMessage);

            if (response != null) {
                sendMessage(session, response);
            }
            
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            
            McpMessage errorResponse = McpMessage.createErrorResponse(
                    null, 
                    McpError.parseError("Failed to parse message: " + e.getMessage())
            );
            
            try {
                sendMessage(session, errorResponse);
            } catch (Exception sendError) {
                log.error("Failed to send error response: {}", sendError.getMessage(), sendError);
            }
        }
    }
    
    /**
     * Send welcome message to newly connected client
     */
    private void sendWelcomeMessage(WebSocketSession session) {
        try {
            Map<String, Object> welcomeData = new HashMap<>();
            welcomeData.put("serverName", "Spring Boost MCP Server");
            welcomeData.put("version", "0.1.0");
            welcomeData.put("availableTools", toolRegistry.getToolNames().size());
            welcomeData.put("timestamp", System.currentTimeMillis());
            
            McpMessage welcome = McpMessage.createNotification("welcome", welcomeData);
            sendMessage(session, welcome);
            
        } catch (Exception e) {
            log.error("Failed to send welcome message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Send a message to a WebSocket session
     */
    private void sendMessage(WebSocketSession session, McpMessage message) throws IOException {
        String json = objectMapper.writeValueAsString(message);
        log.debug("Sending message: {}", json);
        
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            } else {
                log.warn("Cannot send message to closed session: {}", session.getId());
            }
        }
    }
    
    /**
     * Get server statistics
     */
    public Map<String, Object> getServerStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", activeSessions.size());
        stats.put("totalTools", toolRegistry.getAllTools().size());
        stats.put("messagesProcessed", messageIdCounter.get());
        stats.put("uptime", System.currentTimeMillis());
        
        return stats;
    }
}
