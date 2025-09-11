package com.springboost.mcp.tools;

import com.springboost.config.SpringBoostProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for managing all available MCP tools
 * Handles tool discovery, registration, and execution
 */
@Slf4j
@Component
public class McpToolRegistry {
    
    private final Map<String, McpTool> tools = new HashMap<>();
    private final ApplicationContext applicationContext;
    private final SpringBoostProperties properties;
    
    @Autowired
    public McpToolRegistry(ApplicationContext applicationContext, SpringBoostProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
    }
    
    @PostConstruct
    public void initialize() {
        discoverAndRegisterTools();
        log.info("Initialized MCP Tool Registry with {} tools", tools.size());
    }
    
    /**
     * Discover all MCP tools from the Spring context and register them
     */
    private void discoverAndRegisterTools() {
        Map<String, McpTool> discoveredTools = applicationContext.getBeansOfType(McpTool.class);
        
        for (Map.Entry<String, McpTool> entry : discoveredTools.entrySet()) {
            McpTool tool = entry.getValue();
            
            if (isToolEnabled(tool)) {
                registerTool(tool);
                log.debug("Registered tool: {} [{}]", tool.getName(), tool.getCategory());
            } else {
                log.debug("Tool disabled by configuration: {}", tool.getName());
            }
        }
    }
    
    /**
     * Register a single tool
     */
    public void registerTool(McpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }
        
        String toolName = tool.getName();
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }
        
        if (tools.containsKey(toolName)) {
            log.warn("Tool with name '{}' already exists. Replacing with new implementation.", toolName);
        }
        
        tools.put(toolName, tool);
    }
    
    /**
     * Get a tool by name
     */
    public Optional<McpTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }
    
    /**
     * Get all registered tools
     */
    public Collection<McpTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }
    
    /**
     * Get tools by category
     */
    public List<McpTool> getToolsByCategory(String category) {
        return tools.values().stream()
                .filter(tool -> category.equals(tool.getCategory()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get tool names
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
    
    /**
     * Execute a tool with given parameters
     */
    public Object executeTool(String toolName, Map<String, Object> params) throws McpToolException {
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            throw new McpToolException(toolName, "Tool not found: " + toolName);
        }
        
        // Validate parameters
        tool.validateParameters(params);
        
        // Check if tool requires elevated privileges
        if (tool.requiresElevatedPrivileges() && !hasElevatedPrivileges()) {
            throw new McpToolException(toolName, "Tool requires elevated privileges", 
                    McpToolException.class.hashCode(), "PRIVILEGE_REQUIRED");
        }
        
        try {
            log.debug("Executing tool: {} with params: {}", toolName, params);
            Object result = tool.execute(params);
            log.debug("Tool execution completed: {}", toolName);
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed: {} - {}", toolName, e.getMessage(), e);
            throw new McpToolException(toolName, "Tool execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if a tool is enabled based on configuration
     */
    private boolean isToolEnabled(McpTool tool) {
        // Global tools enablement check
        if (!properties.getMcp().getTools().isEnabled()) {
            return false;
        }
        
        // Tool-specific checks based on configuration
        String toolName = tool.getName();
        String category = tool.getCategory();
        
        // Database tools check
        if ("database".equals(category) && !properties.getMcp().getTools().isDatabaseAccess()) {
            return false;
        }
        
        // Code execution tools check
        if ("execution".equals(category) && !properties.getMcp().getTools().isCodeExecution()) {
            return false;
        }
        
        // Web/endpoint tools check
        if ("web".equals(category) && !properties.getMcp().getTools().isEndpointScanning()) {
            return false;
        }
        
        // Logging tools check
        if ("logging".equals(category) && !properties.getMcp().getTools().isLogAccess()) {
            return false;
        }
        
        // Individual tool enablement
        return tool.isEnabled();
    }
    
    /**
     * Check if current context has elevated privileges
     * This is a placeholder for actual security implementation
     */
    private boolean hasElevatedPrivileges() {
        // In development mode, allow elevated operations
        if (!properties.getSecurity().isSandboxEnabled()) {
            return true;
        }
        
        // TODO: Implement actual privilege checking
        return false;
    }
    
    /**
     * Get tool statistics
     */
    public Map<String, Object> getToolStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTools", tools.size());
        
        // Group by category
        Map<String, Long> categoryCount = tools.values().stream()
                .collect(Collectors.groupingBy(McpTool::getCategory, Collectors.counting()));
        stats.put("toolsByCategory", categoryCount);
        
        // Enabled vs disabled
        long enabledCount = tools.values().stream()
                .mapToLong(tool -> isToolEnabled(tool) ? 1 : 0)
                .sum();
        stats.put("enabledTools", enabledCount);
        stats.put("disabledTools", tools.size() - enabledCount);
        
        return stats;
    }
}
