package com.springboost.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Base MCP (Model Context Protocol) message structure
 * Following the JSON-RPC 2.0 specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpMessage {
    
    @JsonProperty("jsonrpc")
    @Builder.Default
    private String jsonrpc = "2.0";
    
    // JSON-RPC 2.0 requires a response's id to be the exact same value AND
    // type as the request's id (number, string, or null) -- a strict client
    // that sent a numeric id and gets back a string id back won't recognize
    // the response as matching, and will hang waiting for one that never
    // arrives in a form it accepts. Object (not String) lets Jackson preserve
    // whatever type it deserialized instead of coercing everything to String.
    @JsonProperty("id")
    private Object id;

    @JsonProperty("method")
    private String method;

    @JsonProperty("params")
    private Map<String, Object> params;

    @JsonProperty("result")
    private Object result;

    @JsonProperty("error")
    private McpError error;

    /**
     * Create a request message
     */
    public static McpMessage createRequest(Object id, String method, Map<String, Object> params) {
        return McpMessage.builder()
                .id(id)
                .method(method)
                .params(params)
                .build();
    }

    /**
     * Create a successful response message
     */
    public static McpMessage createSuccessResponse(Object id, Object result) {
        return McpMessage.builder()
                .id(id)
                .result(result)
                .build();
    }

    /**
     * Create an error response message
     */
    public static McpMessage createErrorResponse(Object id, McpError error) {
        return McpMessage.builder()
                .id(id)
                .error(error)
                .build();
    }
    
    /**
     * Create a notification message (no response expected)
     */
    public static McpMessage createNotification(String method, Map<String, Object> params) {
        return McpMessage.builder()
                .method(method)
                .params(params)
                .build();
    }
    
    // @JsonIgnore on all four: Jackson's bean-property introspection treats
    // any isXxx()/getXxx() method as a serializable field by default. Without
    // this, every message serialized "request"/"response"/"notification"
    // booleans that aren't part of JSON-RPC 2.0 -- a strict client validating
    // the envelope shape (confirmed: Claude Code) silently discards a
    // response with unexpected top-level fields and hangs waiting for one
    // it considers valid, even though the real response already arrived.

    /**
     * Check if this is a request message
     */
    @JsonIgnore
    public boolean isRequest() {
        return method != null && id != null && result == null && error == null;
    }

    /**
     * Check if this is a response message
     */
    @JsonIgnore
    public boolean isResponse() {
        return id != null && method == null && (result != null || error != null);
    }

    /**
     * Check if this is a notification message
     */
    @JsonIgnore
    public boolean isNotification() {
        return method != null && id == null;
    }

    /**
     * Check if this is an error response
     */
    @JsonIgnore
    public boolean isError() {
        return error != null;
    }
}
