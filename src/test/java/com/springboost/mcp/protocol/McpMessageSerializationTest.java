package com.springboost.mcp.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson's default bean-property introspection turns any isXxx() method into
 * a serialized field. McpMessage.isRequest()/isResponse()/isNotification()
 * leaked into every wire message as extra top-level "request"/"response"/
 * "notification" booleans -- not part of JSON-RPC 2.0. Confirmed against a
 * real `claude mcp get` connection: Claude Code silently discarded every
 * response as malformed and hung until its own timeout, even though the
 * daemon had already sent a structurally-correct-except-for-this reply.
 */
class McpMessageSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successResponseSerializesToOnlyJsonRpcFields() throws Exception {
        McpMessage response = McpMessage.createSuccessResponse(1, Map.of("ok", true));

        Map<?, ?> json = objectMapper.readValue(objectMapper.writeValueAsString(response), Map.class);

        assertFalse(json.containsKey("request"), "leaked isRequest() as a field");
        assertFalse(json.containsKey("response"), "leaked isResponse() as a field");
        assertFalse(json.containsKey("notification"), "leaked isNotification() as a field");
        assertTrue(json.containsKey("jsonrpc"));
        assertTrue(json.containsKey("id"));
        assertTrue(json.containsKey("result"));
    }
}
