package com.springboost.mcp.protocol;

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
    
    @JsonProperty("id")
    private String id;
    
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
    public static McpMessage createRequest(String id, String method, Map<String, Object> params) {
        return McpMessage.builder()
                .id(id)
                .method(method)
                .params(params)
                .build();
    }
    
    /**
     * Create a successful response message
     */
    public static McpMessage createSuccessResponse(String id, Object result) {
        return McpMessage.builder()
                .id(id)
                .result(result)
                .build();
    }
    
    /**
     * Create an error response message
     */
    public static McpMessage createErrorResponse(String id, McpError error) {
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
    
    /**
     * Check if this is a request message
     */
    public boolean isRequest() {
        return method != null && id != null && result == null && error == null;
    }
    
    /**
     * Check if this is a response message
     */
    public boolean isResponse() {
        return id != null && method == null && (result != null || error != null);
    }
    
    /**
     * Check if this is a notification message
     */
    public boolean isNotification() {
        return method != null && id == null;
    }
    
    /**
     * Check if this is an error response
     */
    public boolean isError() {
        return error != null;
    }
}
