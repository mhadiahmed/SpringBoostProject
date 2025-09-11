package com.springboost.mcp.tools;


import java.util.Map;

/**
 * Base interface for all MCP tools in Spring Boost
 * 
 * Each tool represents a specific capability that can be invoked by AI assistants
 * to gather information or perform actions on Spring Boot applications.
 */
public interface McpTool {
    
    /**
     * Get the unique name of this tool
     * @return Tool name (e.g., "application-info", "database-query")
     */
    String getName();
    
    /**
     * Get a human-readable description of what this tool does
     * @return Tool description
     */
    String getDescription();
    
    /**
     * Get the JSON schema describing the expected parameters for this tool
     * @return Parameter schema as a Map
     */
    Map<String, Object> getParameterSchema();
    
    /**
     * Execute the tool with the given parameters
     * @param params Parameters provided by the AI assistant
     * @return Result of the tool execution
     * @throws McpToolException if execution fails
     */
    Object execute(Map<String, Object> params) throws McpToolException;
    
    /**
     * Check if this tool is enabled based on current configuration
     * @return true if the tool should be available for use
     */
    default boolean isEnabled() {
        return true;
    }
    
    /**
     * Get the category of this tool for organization purposes
     * @return Tool category (e.g., "database", "web", "configuration")
     */
    default String getCategory() {
        return "general";
    }
    
    /**
     * Check if this tool requires elevated privileges
     * @return true if tool needs special permissions
     */
    default boolean requiresElevatedPrivileges() {
        return false;
    }
    
    /**
     * Get examples of how to use this tool
     * @return List of usage examples
     */
    default Map<String, Object> getUsageExamples() {
        return Map.of();
    }
    
    /**
     * Validate parameters before execution
     * @param params Parameters to validate
     * @throws McpToolException if parameters are invalid
     */
    default void validateParameters(Map<String, Object> params) throws McpToolException {
        // Default implementation does basic null check
        if (params == null) {
            throw new McpToolException("Parameters cannot be null");
        }
    }
}
