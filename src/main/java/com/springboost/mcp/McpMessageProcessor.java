package com.springboost.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboost.mcp.protocol.McpError;
import com.springboost.mcp.protocol.McpMessage;
import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import com.springboost.mcp.tools.McpToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Transport-agnostic JSON-RPC/MCP message handling, shared by every transport
 * (stdio, WebSocket, ...) so the protocol logic is implemented once.
 */
@Slf4j
@Component
public class McpMessageProcessor {

    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Autowired
    public McpMessageProcessor(McpToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Process an incoming MCP message and produce the response (null for notifications).
     */
    public McpMessage process(McpMessage message) {
        if (message.isRequest()) {
            return handleRequest(message);
        } else if (message.isNotification()) {
            handleNotification(message);
            return null;
        } else {
            log.warn("Received unexpected message type: {}", message);
            return null;
        }
    }

    private McpMessage handleRequest(McpMessage request) {
        String method = request.getMethod();
        Object id = request.getId();
        Map<String, Object> params = request.getParams() != null ? request.getParams() : new HashMap<>();

        try {
            switch (method) {
                case "initialize":
                    return handleInitialize(id, params);

                case "tools/list":
                    return handleListTools(id);

                case "tools/call":
                    return handleToolCall(id, params);

                case "ping":
                    return handlePing(id);

                default:
                    return McpMessage.createErrorResponse(id, McpError.methodNotFound(method));
            }
        } catch (Exception e) {
            log.error("Error handling request {}: {}", method, e.getMessage(), e);
            return McpMessage.createErrorResponse(id,
                    McpError.internalError("Request processing failed: " + e.getMessage(), null));
        }
    }

    private static final String FALLBACK_PROTOCOL_VERSION = "2024-11-05";

    private McpMessage handleInitialize(Object id, Map<String, Object> params) {
        // MCP requires the server to negotiate a protocol version, not just
        // echo a version it was built against: a real client (e.g. Claude
        // Code) sends the version IT speaks, and if the server responds with
        // a different one the client treats the handshake as failed/incompatible.
        // This server has no version-specific behavior of its own, so the
        // correct move is to accept whatever the client proposes.
        Object clientProtocolVersion = params.get("protocolVersion");

        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", clientProtocolVersion != null ? clientProtocolVersion : FALLBACK_PROTOCOL_VERSION);
        result.put("serverInfo", Map.of(
                "name", "spring-boost",
                "version", "0.1.0"
        ));
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", true),
                "logging", Map.of(),
                "prompts", Map.of()
        ));

        return McpMessage.createSuccessResponse(id, result);
    }

    private McpMessage handleListTools(Object id) {
        List<Map<String, Object>> toolList = new ArrayList<>();

        for (McpTool tool : toolRegistry.getAllTools()) {
            Map<String, Object> toolInfo = new HashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            toolInfo.put("inputSchema", tool.getParameterSchema());

            toolList.add(toolInfo);
        }

        Map<String, Object> result = Map.of("tools", toolList);
        return McpMessage.createSuccessResponse(id, result);
    }

    private McpMessage handleToolCall(Object id, Map<String, Object> params) {
        try {
            String toolName = (String) params.get("name");
            if (toolName == null) {
                return McpMessage.createErrorResponse(id,
                        McpError.invalidParams("Tool name is required"));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            if (arguments == null) {
                arguments = new HashMap<>();
            }

            Object result = toolRegistry.executeTool(toolName, arguments);

            Map<String, Object> response = new HashMap<>();
            response.put("content", List.of(Map.of(
                    "type", "text",
                    "text", objectMapper.writeValueAsString(result)
            )));
            response.put("isError", false);

            return McpMessage.createSuccessResponse(id, response);

        } catch (McpToolException e) {
            log.error("Tool execution error: {}", e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("content", List.of(Map.of(
                    "type", "text",
                    "text", "Error: " + e.getMessage()
            )));
            errorResponse.put("isError", true);

            return McpMessage.createSuccessResponse(id, errorResponse);

        } catch (Exception e) {
            log.error("Unexpected error during tool execution: {}", e.getMessage(), e);
            return McpMessage.createErrorResponse(id,
                    McpError.toolExecutionError("Unexpected error: " + e.getMessage(), null));
        }
    }

    private McpMessage handlePing(Object id) {
        return McpMessage.createSuccessResponse(id, Map.of("pong", true));
    }

    private void handleNotification(McpMessage notification) {
        String method = notification.getMethod();
        log.debug("Received notification: {}", method);

        switch (method) {
            case "notifications/initialized":
                log.info("Client initialized successfully");
                break;

            default:
                log.debug("Unknown notification method: {}", method);
        }
    }
}
