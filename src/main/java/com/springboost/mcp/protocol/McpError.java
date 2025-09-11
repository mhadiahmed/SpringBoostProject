package com.springboost.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Error structure following JSON-RPC 2.0 error format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpError {
    
    @JsonProperty("code")
    private int code;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("data")
    private Object data;
    
    // Standard JSON-RPC 2.0 error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    
    // Spring Boost specific error codes
    public static final int TOOL_NOT_FOUND = -32001;
    public static final int TOOL_EXECUTION_ERROR = -32002;
    public static final int DATABASE_ERROR = -32003;
    public static final int SECURITY_ERROR = -32004;
    public static final int CONFIGURATION_ERROR = -32005;
    
    /**
     * Create a parse error
     */
    public static McpError parseError(String message) {
        return McpError.builder()
                .code(PARSE_ERROR)
                .message(message != null ? message : "Parse error")
                .build();
    }
    
    /**
     * Create an invalid request error
     */
    public static McpError invalidRequest(String message) {
        return McpError.builder()
                .code(INVALID_REQUEST)
                .message(message != null ? message : "Invalid request")
                .build();
    }
    
    /**
     * Create a method not found error
     */
    public static McpError methodNotFound(String method) {
        return McpError.builder()
                .code(METHOD_NOT_FOUND)
                .message("Method not found: " + method)
                .build();
    }
    
    /**
     * Create an invalid params error
     */
    public static McpError invalidParams(String message) {
        return McpError.builder()
                .code(INVALID_PARAMS)
                .message(message != null ? message : "Invalid params")
                .build();
    }
    
    /**
     * Create an internal error
     */
    public static McpError internalError(String message, Object data) {
        return McpError.builder()
                .code(INTERNAL_ERROR)
                .message(message != null ? message : "Internal error")
                .data(data)
                .build();
    }
    
    /**
     * Create a tool not found error
     */
    public static McpError toolNotFound(String toolName) {
        return McpError.builder()
                .code(TOOL_NOT_FOUND)
                .message("Tool not found: " + toolName)
                .build();
    }
    
    /**
     * Create a tool execution error
     */
    public static McpError toolExecutionError(String message, Object data) {
        return McpError.builder()
                .code(TOOL_EXECUTION_ERROR)
                .message(message)
                .data(data)
                .build();
    }
    
    /**
     * Create a database error
     */
    public static McpError databaseError(String message) {
        return McpError.builder()
                .code(DATABASE_ERROR)
                .message("Database error: " + message)
                .build();
    }
    
    /**
     * Create a security error
     */
    public static McpError securityError(String message) {
        return McpError.builder()
                .code(SECURITY_ERROR)
                .message("Security error: " + message)
                .build();
    }
    
    /**
     * Create a configuration error
     */
    public static McpError configurationError(String message) {
        return McpError.builder()
                .code(CONFIGURATION_ERROR)
                .message("Configuration error: " + message)
                .build();
    }
}
