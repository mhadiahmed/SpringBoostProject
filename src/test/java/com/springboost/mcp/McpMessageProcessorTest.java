package com.springboost.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboost.mcp.protocol.McpMessage;
import com.springboost.mcp.tools.McpToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Real clients (Claude Code, etc.) send the protocol version THEY speak in
 * "initialize" and reject the handshake if the server echoes back a
 * different one -- confirmed against a real `claude mcp get` connection,
 * which failed silently until this negotiation was fixed.
 */
class McpMessageProcessorTest {

    private final McpMessageProcessor processor = new McpMessageProcessor(mock(McpToolRegistry.class), new ObjectMapper());

    @Test
    void initializeEchoesBackTheClientsRequestedProtocolVersion() {
        McpMessage request = McpMessage.createRequest(1, "initialize",
                Map.of("protocolVersion", "2025-11-25", "capabilities", Map.of()));

        McpMessage response = processor.process(request);

        assertEquals(1, response.getId());
        assertEquals("2025-11-25", ((Map<?, ?>) response.getResult()).get("protocolVersion"));
    }

    @Test
    void initializeFallsBackToADefaultVersionWhenClientOmitsOne() {
        McpMessage request = McpMessage.createRequest("abc", "initialize", Map.of());

        McpMessage response = processor.process(request);

        assertEquals("abc", response.getId());
        assertEquals("2024-11-05", ((Map<?, ?>) response.getResult()).get("protocolVersion"));
    }
}
