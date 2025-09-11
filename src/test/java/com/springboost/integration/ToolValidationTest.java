package com.springboost.integration;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import com.springboost.mcp.tools.McpToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive validation tests for all MCP tools
 * Validates tool contracts, parameter schemas, and response formats
 */
@SharedTestConfiguration
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolValidationTest {

    @Autowired
    private McpToolRegistry toolRegistry;

    private List<McpTool> availableTools;

    // Expected tool categories
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "application", "database", "web", "logging", "documentation", 
            "testing", "monitoring", "security", "development"
    );

    @BeforeEach
    void setUp() {
        availableTools = new ArrayList<>(toolRegistry.getAllTools());
        assertFalse(availableTools.isEmpty(), "Should have tools registered");
    }

    @Test
    @Order(1)
    void testAllToolsHaveValidBasicProperties() {
        for (McpTool tool : availableTools) {
            // Test name
            assertNotNull(tool.getName(), "Tool should have a name");
            assertFalse(tool.getName().trim().isEmpty(), "Tool name should not be empty");
            assertTrue(tool.getName().matches("^[a-z0-9-]+$"), 
                "Tool name '" + tool.getName() + "' should be lowercase with hyphens only");

            // Test description
            assertNotNull(tool.getDescription(), "Tool '" + tool.getName() + "' should have a description");
            assertFalse(tool.getDescription().trim().isEmpty(), 
                "Tool '" + tool.getName() + "' description should not be empty");
            assertTrue(tool.getDescription().length() >= 10, 
                "Tool '" + tool.getName() + "' description should be at least 10 characters");

            // Test category
            assertNotNull(tool.getCategory(), "Tool '" + tool.getName() + "' should have a category");
            assertTrue(VALID_CATEGORIES.contains(tool.getCategory()), 
                "Tool '" + tool.getName() + "' has invalid category: " + tool.getCategory());
        }
    }

    @Test
    @Order(2)
    void testAllToolsHaveValidParameterSchemas() {
        for (McpTool tool : availableTools) {
            Map<String, Object> schema = tool.getParameterSchema();
            
            assertNotNull(schema, "Tool '" + tool.getName() + "' should have a parameter schema");
            
            // Validate top-level schema properties
            assertEquals("object", schema.get("type"), 
                "Tool '" + tool.getName() + "' schema should be of type 'object'");
            
            Object properties = schema.get("properties");
            if (properties != null) {
                assertTrue(properties instanceof Map, 
                    "Tool '" + tool.getName() + "' properties should be a Map");
                
                @SuppressWarnings("unchecked")
                Map<String, Object> propMap = (Map<String, Object>) properties;
                
                // Validate each property
                for (Map.Entry<String, Object> prop : propMap.entrySet()) {
                    validatePropertySchema(tool.getName(), prop.getKey(), prop.getValue());
                }
            }
            
            // Validate required array if present
            Object required = schema.get("required");
            if (required != null) {
                assertTrue(required instanceof List, 
                    "Tool '" + tool.getName() + "' required should be a List");
            }
        }
    }

    @Test
    @Order(3)
    void testAllToolsHaveUsageExamples() {
        for (McpTool tool : availableTools) {
            Map<String, Object> examples = tool.getUsageExamples();
            
            if (examples != null && !examples.isEmpty()) {
                // Validate usage examples structure
                for (Map.Entry<String, Object> example : examples.entrySet()) {
                    assertNotNull(example.getKey(), 
                        "Tool '" + tool.getName() + "' usage example should have a key");
                    assertNotNull(example.getValue(), 
                        "Tool '" + tool.getName() + "' usage example should have a value");
                    
                    if (example.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> exampleMap = (Map<String, Object>) example.getValue();
                        
                        assertNotNull(exampleMap.get("description"), 
                            "Tool '" + tool.getName() + "' usage example '" + example.getKey() + 
                            "' should have a description");
                        assertNotNull(exampleMap.get("parameters"), 
                            "Tool '" + tool.getName() + "' usage example '" + example.getKey() + 
                            "' should have parameters");
                    }
                }
            }
        }
    }

    @Test
    @Order(4)
    void testToolExecutionWithEmptyParameters() {
        for (McpTool tool : availableTools) {
            try {
                Object result = tool.execute(Map.of());
                // Tool should either succeed or throw a meaningful exception
                if (result != null) {
                    // If it succeeds, result should be valid
                    validateToolResult(tool.getName(), result);
                }
            } catch (McpToolException e) {
                // Expected for tools that require parameters
                assertNotNull(e.getMessage(), 
                    "Tool '" + tool.getName() + "' exception should have a message");
                assertFalse(e.getMessage().trim().isEmpty(), 
                    "Tool '" + tool.getName() + "' exception message should not be empty");
            } catch (Exception e) {
                fail("Tool '" + tool.getName() + "' threw unexpected exception: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(5)
    void testToolExecutionWithNullParameters() {
        for (McpTool tool : availableTools) {
            assertThrows(Exception.class, () -> {
                tool.execute(null);
            }, "Tool '" + tool.getName() + "' should handle null parameters gracefully");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "application-info", "database-connections", "database-schema", 
        "list-endpoints", "search-docs", "documentation-management"
    })
    @Order(6)
    void testSpecificToolExecution(String toolName) {
        McpTool tool = findTool(toolName);
        
        Map<String, Object> examples = tool.getUsageExamples();
        if (examples != null && !examples.isEmpty()) {
            // Try to execute the first valid example
            for (Map.Entry<String, Object> example : examples.entrySet()) {
                if (example.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> exampleMap = (Map<String, Object>) example.getValue();
                    
                    Object parameters = exampleMap.get("parameters");
                    if (parameters instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) parameters;
                        
                        try {
                            Object result = tool.execute(params);
                            validateToolResult(toolName, result);
                            return; // Success, no need to try other examples
                        } catch (Exception e) {
                            // Log but continue to next example
                            System.out.println("Example failed for " + toolName + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Test
    @Order(7)
    void testToolResponseTimeConsistency() throws Exception {
        McpTool applicationInfoTool = findTool("application-info");
        
        // Measure response times over multiple executions
        long[] responseTimes = new long[10];
        
        for (int i = 0; i < 10; i++) {
            long startTime = System.nanoTime();
            applicationInfoTool.execute(Map.of());
            long endTime = System.nanoTime();
            
            responseTimes[i] = (endTime - startTime) / 1_000_000; // Convert to milliseconds
        }
        
        // Calculate statistics
        long minTime = responseTimes[0];
        long maxTime = responseTimes[0];
        long totalTime = 0;
        
        for (long time : responseTimes) {
            minTime = Math.min(minTime, time);
            maxTime = Math.max(maxTime, time);
            totalTime += time;
        }
        
        double avgTime = totalTime / (double) responseTimes.length;
        
        // Validate response time consistency
        assertTrue(avgTime < 1000, "Average response time should be less than 1 second: " + avgTime + "ms");
        assertTrue(maxTime < 5000, "Maximum response time should be less than 5 seconds: " + maxTime + "ms");
        
        // Variance shouldn't be too high (max shouldn't be more than 10x min)
        assertTrue(maxTime <= minTime * 10, 
            "Response time variance too high. Min: " + minTime + "ms, Max: " + maxTime + "ms");
    }

    @Test
    @Order(8)
    void testToolCategories() {
        Map<String, Long> categoryCount = availableTools.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        McpTool::getCategory,
                        java.util.stream.Collectors.counting()
                ));
        
        // Verify we have tools in major categories
        assertTrue(categoryCount.containsKey("application"), "Should have application tools");
        assertTrue(categoryCount.containsKey("database"), "Should have database tools");
        assertTrue(categoryCount.containsKey("documentation"), "Should have documentation tools");
        
        // Each category should have at least one tool
        for (Map.Entry<String, Long> entry : categoryCount.entrySet()) {
            assertTrue(entry.getValue() > 0, 
                "Category '" + entry.getKey() + "' should have at least one tool");
        }
    }

    @Test
    @Order(9)
    void testToolNameUniqueness() {
        Set<String> toolNames = new java.util.HashSet<>();
        
        for (McpTool tool : availableTools) {
            String name = tool.getName();
            assertFalse(toolNames.contains(name), 
                "Tool name '" + name + "' is not unique");
            toolNames.add(name);
        }
        
        assertEquals(availableTools.size(), toolNames.size(), 
            "All tool names should be unique");
    }

    @Test
    @Order(10)
    void testToolErrorMessagesQuality() {
        for (McpTool tool : availableTools) {
            try {
                // Try to execute with obviously invalid parameters
                Map<String, Object> invalidParams = Map.of(
                        "nonExistentParam", "invalidValue",
                        "anotherInvalidParam", -999
                );
                
                tool.execute(invalidParams);
                
            } catch (McpToolException e) {
                // Validate error message quality
                String message = e.getMessage();
                assertNotNull(message, "Tool '" + tool.getName() + "' should provide error message");
                assertFalse(message.trim().isEmpty(), 
                    "Tool '" + tool.getName() + "' error message should not be empty");
                assertTrue(message.length() >= 10, 
                    "Tool '" + tool.getName() + "' error message should be descriptive");
                
                // Error message should contain tool name for context
                assertTrue(message.contains(tool.getName()) || message.toLowerCase().contains("parameter"), 
                    "Tool '" + tool.getName() + "' error message should provide context");
                    
            } catch (Exception e) {
                // Other exceptions are acceptable but should have meaningful messages
                assertNotNull(e.getMessage(), 
                    "Tool '" + tool.getName() + "' exception should have a message");
            }
        }
    }

    private void validatePropertySchema(String toolName, String propName, Object propSchema) {
        assertTrue(propSchema instanceof Map, 
            "Tool '" + toolName + "' property '" + propName + "' should be a Map");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> propMap = (Map<String, Object>) propSchema;
        
        // Should have a type
        Object type = propMap.get("type");
        assertNotNull(type, 
            "Tool '" + toolName + "' property '" + propName + "' should have a type");
        
        // Should have a description
        Object description = propMap.get("description");
        assertNotNull(description, 
            "Tool '" + toolName + "' property '" + propName + "' should have a description");
        assertTrue(description instanceof String, 
            "Tool '" + toolName + "' property '" + propName + "' description should be a String");
        assertFalse(((String) description).trim().isEmpty(), 
            "Tool '" + toolName + "' property '" + propName + "' description should not be empty");
    }

    private void validateToolResult(String toolName, Object result) {
        assertNotNull(result, "Tool '" + toolName + "' should return a non-null result");
        
        // Most tools should return a Map with structured data
        if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            
            assertFalse(resultMap.isEmpty(), 
                "Tool '" + toolName + "' should return non-empty result");
        }
        
        // String results should not be empty
        if (result instanceof String) {
            assertFalse(((String) result).trim().isEmpty(), 
                "Tool '" + toolName + "' string result should not be empty");
        }
    }

    private McpTool findTool(String toolName) {
        return availableTools.stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool '" + toolName + "' not found"));
    }
}
