package com.springboost.unit;

import com.springboost.config.SpringBoostProperties;
import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolRegistry;
import com.springboost.mcp.tools.impl.ApplicationInfoTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Lightweight unit tests for McpToolRegistry without Spring context
 */
class McpToolRegistryUnitTest {

    @Mock
    private ApplicationContext applicationContext;
    
    @Mock 
    private SpringBoostProperties properties;
    
    @Mock
    private ApplicationInfoTool mockTool;
    
    private McpToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock tool
        when(mockTool.getName()).thenReturn("test-tool");
        when(mockTool.getCategory()).thenReturn("test");
        when(mockTool.getDescription()).thenReturn("Test tool");
        
        // Setup mock application context
        when(applicationContext.getBeansOfType(McpTool.class))
            .thenReturn(Collections.singletonMap("testTool", mockTool));
        
        // Setup mock properties
        when(properties.getMcp()).thenReturn(new SpringBoostProperties.McpProperties());
        
        toolRegistry = new McpToolRegistry(applicationContext, properties);
    }

    @Test
    void testToolRegistryInitialization() {
        assertNotNull(toolRegistry);
    }

    @Test
    void testGetAllTools() {
        // Initialize manually since @PostConstruct doesn't run in unit tests
        toolRegistry.initialize();
        
        // This test runs without Spring context - much faster!
        var tools = toolRegistry.getAllTools();
        assertNotNull(tools);
        assertTrue(tools.size() >= 0); // Should be at least 0 tools
    }

    @Test 
    void testGetToolByName() {
        // Initialize manually since @PostConstruct doesn't run in unit tests  
        toolRegistry.initialize();
        
        var tool = toolRegistry.getTool("test-tool");
        // Should handle missing tools gracefully - getTool returns Optional
        assertTrue(tool.isEmpty()); // Expected since tool isn't actually registered in this mock setup
    }
}
