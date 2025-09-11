package com.springboost.mcp.tools;

/**
 * Exception thrown when MCP tool execution fails
 */
public class McpToolException extends Exception {
    
    private final String toolName;
    private final int errorCode;
    private final Object errorData;
    
    public McpToolException(String message) {
        super(message);
        this.toolName = null;
        this.errorCode = -1;
        this.errorData = null;
    }
    
    public McpToolException(String message, Throwable cause) {
        super(message, cause);
        this.toolName = null;
        this.errorCode = -1;
        this.errorData = null;
    }
    
    public McpToolException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
        this.errorCode = -1;
        this.errorData = null;
    }
    
    public McpToolException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = -1;
        this.errorData = null;
    }
    
    public McpToolException(String toolName, String message, int errorCode, Object errorData) {
        super(message);
        this.toolName = toolName;
        this.errorCode = errorCode;
        this.errorData = errorData;
    }
    
    public McpToolException(String toolName, String message, Throwable cause, int errorCode, Object errorData) {
        super(message, cause);
        this.toolName = toolName;
        this.errorCode = errorCode;
        this.errorData = errorData;
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public int getErrorCode() {
        return errorCode;
    }
    
    public Object getErrorData() {
        return errorData;
    }
    
    public boolean hasErrorCode() {
        return errorCode != -1;
    }
}
