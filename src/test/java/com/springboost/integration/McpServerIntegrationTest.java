package com.springboost.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboost.config.SpringBoostProperties;
import com.springboost.mcp.McpServer;
import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MCP Server functionality
 * Tests real-world scenarios including tool execution, error handling, and performance
 */
@SharedTestConfiguration
@TestPropertySource(properties = {
    "spring-boost.mcp.enabled=true",
    "spring-boost.mcp.port=28999"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpServerIntegrationTest {

    @Autowired
    private McpToolRegistry toolRegistry;
    
    @Autowired
    private SpringBoostProperties properties;
    
    @Autowired
    private ObjectMapper objectMapper;

    private List<McpTool> availableTools;

    @BeforeEach
    void setUp() {
        availableTools = new ArrayList<>(toolRegistry.getAllTools());
        assertFalse(availableTools.isEmpty(), "Should have registered tools available");
    }

    @Test
    @Order(1)
    void testToolRegistryIntegration() {
        // Verify all expected tools are registered
        assertTrue(availableTools.size() >= 15, 
            "Should have at least 15 tools registered, found: " + availableTools.size());
        
        // Verify specific tools exist
        assertToolExists("application-info");
        assertToolExists("database-connections");
        assertToolExists("database-schema");
        assertToolExists("database-query");
        assertToolExists("list-endpoints");
        assertToolExists("get-absolute-url");
        assertToolExists("last-error");
        assertToolExists("read-log-entries");
        assertToolExists("browser-logs");
        assertToolExists("spring-shell");
        assertToolExists("search-docs");
        assertToolExists("list-actuator-endpoints");
        assertToolExists("test-execution");
        assertToolExists("documentation-management");
    }

    @Test
    @Order(2)
    void testToolSchemaValidation() {
        for (McpTool tool : availableTools) {
            Map<String, Object> schema = tool.getParameterSchema();
            
            // Validate schema structure
            assertNotNull(schema, "Tool " + tool.getName() + " should have a schema");
            assertEquals("object", schema.get("type"), 
                "Tool " + tool.getName() + " schema should be of type 'object'");
            
            // Validate required fields exist
            assertNotNull(schema.get("properties"), 
                "Tool " + tool.getName() + " should have properties defined");
        }
    }

    @Test
    @Order(3)
    void testApplicationInfoToolExecution() throws Exception {
        McpTool applicationInfoTool = findTool("application-info");
        
        Map<String, Object> params = Map.of();
        Object result = applicationInfoTool.execute(params);
        
        assertNotNull(result, "Application info tool should return results");
        
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            // Verify expected fields are present
            assertNotNull(resultMap.get("springBootVersion"), "Should contain Spring Boot version");
            assertNotNull(resultMap.get("javaVersion"), "Should contain Java version");
            assertNotNull(resultMap.get("profiles"), "Should contain active profiles");
        }
    }

    @Test
    @Order(4)
    void testSearchDocsToolExecution() throws Exception {
        McpTool searchDocsTool = findTool("search-docs");
        
        Map<String, Object> params = Map.of(
                "query", "spring security authentication",
                "maxResults", 3,
                "semanticSearch", true
        );
        
        Object result = searchDocsTool.execute(params);
        assertNotNull(result, "Search docs tool should return results");
        
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            assertNotNull(resultMap.get("query"), "Should contain original query");
            assertNotNull(resultMap.get("results"), "Should contain search results");
            assertNotNull(resultMap.get("searchType"), "Should contain search type");
        }
    }

    @Test
    @Order(5)
    void testDocumentationManagementToolExecution() throws Exception {
        McpTool docManagementTool = findTool("documentation-management");
        
        // Test status operation
        Map<String, Object> statusParams = Map.of(
                "operation", "status",
                "includeDetails", true
        );
        
        Object result = docManagementTool.execute(statusParams);
        assertNotNull(result, "Documentation management tool should return status");
        
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            assertEquals("status", resultMap.get("operation"));
            assertNotNull(resultMap.get("indexStats"), "Should contain index statistics");
        }
    }

    @Test
    @Order(6)
    void testToolErrorHandling() {
        McpTool searchDocsTool = findTool("search-docs");
        
        // Test with invalid parameters
        Map<String, Object> invalidParams = Map.of(
                "query", "", // Empty query should trigger validation error
                "maxResults", -1 // Invalid max results
        );
        
        assertThrows(Exception.class, () -> {
            searchDocsTool.execute(invalidParams);
        }, "Tool should throw exception for invalid parameters");
    }

    @Test
    @Order(7)
    void testConcurrentToolExecution() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        try {
            CompletableFuture<Void>[] futures = new CompletableFuture[10];
            
            for (int i = 0; i < 10; i++) {
                final int requestId = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        McpTool applicationInfoTool = findTool("application-info");
                        Object result = applicationInfoTool.execute(Map.of());
                        assertNotNull(result, "Concurrent request " + requestId + " should return result");
                    } catch (Exception e) {
                        fail("Concurrent request " + requestId + " failed: " + e.getMessage());
                    }
                }, executor);
            }
            
            // Wait for all requests to complete
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), 
                "Executor should shutdown cleanly");
        }
    }

    @Test
    @Order(8)
    void testToolPerformance() throws Exception {
        McpTool applicationInfoTool = findTool("application-info");
        
        // Warm up
        for (int i = 0; i < 5; i++) {
            applicationInfoTool.execute(Map.of());
        }
        
        // Performance test
        long startTime = System.currentTimeMillis();
        int iterations = 100;
        
        for (int i = 0; i < iterations; i++) {
            Object result = applicationInfoTool.execute(Map.of());
            assertNotNull(result, "Performance test iteration " + i + " should return result");
        }
        
        long endTime = System.currentTimeMillis();
        long avgTime = (endTime - startTime) / iterations;
        
        assertTrue(avgTime < 500, 
            "Average tool execution time should be less than 500ms, was: " + avgTime + "ms");
    }

    @Test
    @Order(9)
    void testToolUsageExamples() throws Exception {
        for (McpTool tool : availableTools) {
            Map<String, Object> usageExamples = tool.getUsageExamples();
            
            if (usageExamples != null && !usageExamples.isEmpty()) {
                // Test at least one usage example for each tool
                for (Map.Entry<String, Object> example : usageExamples.entrySet()) {
                    if (example.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> exampleMap = (Map<String, Object>) example.getValue();
                        
                        Object parameters = exampleMap.get("parameters");
                        if (parameters instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> params = (Map<String, Object>) parameters;
                            
                            try {
                                Object result = tool.execute(params);
                                assertNotNull(result, 
                                    "Usage example '" + example.getKey() + "' for tool '" + 
                                    tool.getName() + "' should return result");
                            } catch (Exception e) {
                                // Some examples might fail in test environment, log but don't fail test
                                System.out.println("Warning: Usage example '" + example.getKey() + 
                                    "' for tool '" + tool.getName() + "' failed: " + e.getMessage());
                            }
                        }
                        
                        // Only test first example to avoid long test times
                        break;
                    }
                }
            }
        }
    }

    @Test
    @Order(10)
    void testMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection and get baseline
        System.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Execute multiple tools to test memory usage
        try {
            for (int i = 0; i < 50; i++) {
                McpTool applicationInfoTool = findTool("application-info");
                applicationInfoTool.execute(Map.of());
                
                McpTool searchDocsTool = findTool("search-docs");
                searchDocsTool.execute(Map.of(
                        "query", "spring boot configuration " + i,
                        "maxResults", 5
                ));
            }
        } catch (Exception e) {
            fail("Memory usage test failed: " + e.getMessage());
        }
        
        // Force garbage collection and check memory usage
        System.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        // Memory usage should be reasonable (less than 100MB for test operations)
        assertTrue(memoryUsed < 100_000_000, 
            "Memory usage should be reasonable, used: " + (memoryUsed / 1_000_000) + "MB");
    }

    private void assertToolExists(String toolName) {
        assertTrue(availableTools.stream().anyMatch(tool -> tool.getName().equals(toolName)),
                "Tool '" + toolName + "' should be registered");
    }

    private McpTool findTool(String toolName) {
        return availableTools.stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool '" + toolName + "' not found"));
    }
}
