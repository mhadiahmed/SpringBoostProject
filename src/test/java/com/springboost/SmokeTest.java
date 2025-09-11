package com.springboost;

import com.springboost.mcp.tools.McpToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fast smoke test to verify basic functionality without heavy components
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("minimal")
class SmokeTest {

    @Autowired
    private McpToolRegistry toolRegistry;

    @Test
    void contextLoads() {
        assertNotNull(toolRegistry);
    }

    @Test
    void toolRegistryHasTools() {
        var tools = toolRegistry.getAllTools();
        assertNotNull(tools);
        // Should have at least a few basic tools even in minimal mode
        assertTrue(tools.size() >= 1, "Expected at least 1 tool to be registered");
    }
}
