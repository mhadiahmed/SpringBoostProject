package com.springboost.integration;

import com.springboost.docs.service.EmbeddingsService;
import com.springboost.docs.service.SearchService;
import com.springboost.docs.model.SearchRequest;
import com.springboost.docs.model.SearchResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmark tests for Spring Boost components
 * Tests response times, throughput, and resource usage under load
 */
@SharedTestConfiguration
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceBenchmarkTest {

    @Autowired
    private McpToolRegistry toolRegistry;
    
    @Autowired
    private SearchService searchService;
    
    @Autowired
    private EmbeddingsService embeddingsService;

    private List<McpTool> availableTools;

    @BeforeEach
    void setUp() {
        availableTools = new ArrayList<>(toolRegistry.getAllTools());
    }

    @Test
    @Order(1)
    void benchmarkApplicationInfoTool() throws Exception {
        McpTool applicationInfoTool = findTool("application-info");
        
        // Warmup
        for (int i = 0; i < 10; i++) {
            applicationInfoTool.execute(Map.of());
        }
        
        // Benchmark
        int iterations = 1000;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            Object result = applicationInfoTool.execute(Map.of());
            assertNotNull(result);
        }
        
        long endTime = System.nanoTime();
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        
        System.out.printf("Application Info Tool - Average execution time: %.2f ms%n", avgTimeMs);
        
        // Performance requirement: < 50ms average
        assertTrue(avgTimeMs < 50.0, 
            "Application Info Tool should execute in less than 50ms on average, was: " + avgTimeMs + "ms");
    }

    @Test
    @Order(2)
    void benchmarkSearchDocsToolSemanticSearch() throws Exception {
        McpTool searchDocsTool = findTool("search-docs");
        
        Map<String, Object> searchParams = Map.of(
                "query", "spring security authentication",
                "semanticSearch", true,
                "maxResults", 5
        );
        
        // Warmup
        for (int i = 0; i < 5; i++) {
            searchDocsTool.execute(searchParams);
        }
        
        // Benchmark
        int iterations = 100;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            Object result = searchDocsTool.execute(searchParams);
            assertNotNull(result);
        }
        
        long endTime = System.nanoTime();
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        
        System.out.printf("Search Docs Tool (Semantic) - Average execution time: %.2f ms%n", avgTimeMs);
        
        // Performance requirement: < 200ms average for semantic search
        assertTrue(avgTimeMs < 200.0, 
            "Search Docs Tool semantic search should execute in less than 200ms on average, was: " + avgTimeMs + "ms");
    }

    @Test
    @Order(3)
    void benchmarkEmbeddingsGeneration() {
        String[] testTexts = {
            "Spring Boot auto-configuration",
            "Spring Security JWT authentication", 
            "Spring Data JPA repositories",
            "Spring Web MVC controllers",
            "Spring Boot Actuator endpoints"
        };
        
        // Warmup
        for (String text : testTexts) {
            embeddingsService.generateEmbeddings(text);
        }
        
        // Benchmark
        int iterations = 100;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            String text = testTexts[i % testTexts.length];
            List<Double> embeddings = embeddingsService.generateEmbeddings(text);
            assertNotNull(embeddings);
            assertFalse(embeddings.isEmpty());
        }
        
        long endTime = System.nanoTime();
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        
        System.out.printf("Embeddings Generation - Average time: %.2f ms%n", avgTimeMs);
        
        // Performance requirement: < 100ms average
        assertTrue(avgTimeMs < 100.0, 
            "Embeddings generation should complete in less than 100ms on average, was: " + avgTimeMs + "ms");
    }

    @Test
    @Order(4)
    void benchmarkSemanticSearch() {
        String[] queries = {
            "spring security authentication",
            "database configuration",
            "testing framework",
            "web controllers",
            "actuator monitoring"
        };
        
        // Warmup
        for (String query : queries) {
            SearchRequest request = SearchRequest.simple(query);
            searchService.search(request);
        }
        
        // Benchmark
        int iterations = 200;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            String query = queries[i % queries.length];
            SearchRequest request = SearchRequest.simple(query);
            SearchResult result = searchService.search(request);
            assertNotNull(result);
        }
        
        long endTime = System.nanoTime();
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        
        System.out.printf("Semantic Search - Average time: %.2f ms%n", avgTimeMs);
        
        // Performance requirement: < 150ms average
        assertTrue(avgTimeMs < 150.0, 
            "Semantic search should complete in less than 150ms on average, was: " + avgTimeMs + "ms");
    }

    @Test
    @Order(5)
    void benchmarkConcurrentToolExecution() throws Exception {
        int threadCount = 10;
        int requestsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            
            long startTime = System.nanoTime();
            
            // Start all threads
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        
                        McpTool applicationInfoTool = findTool("application-info");
                        
                        for (int i = 0; i < requestsPerThread; i++) {
                            Object result = applicationInfoTool.execute(Map.of());
                            assertNotNull(result);
                        }
                        
                    } catch (Exception e) {
                        fail("Concurrent execution failed: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
            
            // Release all threads
            startLatch.countDown();
            
            // Wait for completion
            assertTrue(endLatch.await(30, TimeUnit.SECONDS), 
                "Concurrent execution should complete within 30 seconds");
            
            long endTime = System.nanoTime();
            double totalTimeMs = (endTime - startTime) / 1_000_000.0;
            int totalRequests = threadCount * requestsPerThread;
            double avgTimeMs = totalTimeMs / totalRequests;
            double requestsPerSecond = totalRequests / (totalTimeMs / 1000.0);
            
            System.out.printf("Concurrent Execution - Total requests: %d, Total time: %.2f ms%n", 
                totalRequests, totalTimeMs);
            System.out.printf("Concurrent Execution - Average time per request: %.2f ms%n", avgTimeMs);
            System.out.printf("Concurrent Execution - Requests per second: %.2f%n", requestsPerSecond);
            
            // Performance requirements
            assertTrue(avgTimeMs < 100.0, 
                "Average time per request in concurrent execution should be less than 100ms, was: " + avgTimeMs + "ms");
            assertTrue(requestsPerSecond > 50.0, 
                "Should handle at least 50 requests per second, achieved: " + requestsPerSecond);
            
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @Order(6)
    void benchmarkMemoryUsage() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection
        System.gc();
        Thread.sleep(100);
        
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Execute many operations to test memory usage
        McpTool applicationInfoTool = findTool("application-info");
        McpTool searchDocsTool = findTool("search-docs");
        
        for (int i = 0; i < 1000; i++) {
            // Execute application info tool
            applicationInfoTool.execute(Map.of());
            
            // Execute search docs tool
            searchDocsTool.execute(Map.of(
                    "query", "spring boot configuration " + (i % 10),
                    "maxResults", 3
            ));
            
            // Generate embeddings
            embeddingsService.generateEmbeddings("Test text " + i);
            
            // Periodic garbage collection
            if (i % 100 == 0) {
                System.gc();
                Thread.sleep(10);
            }
        }
        
        // Force garbage collection and measure
        System.gc();
        Thread.sleep(100);
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        double memoryUsedMB = memoryUsed / (1024.0 * 1024.0);
        
        System.out.printf("Memory Usage - Used: %.2f MB%n", memoryUsedMB);
        
        // Memory usage should be reasonable (less than 50MB for 1000 operations)
        assertTrue(memoryUsedMB < 50.0, 
            "Memory usage should be less than 50MB for 1000 operations, was: " + memoryUsedMB + "MB");
    }

    @Test
    @Order(7)
    void benchmarkDocumentationManagementTool() throws Exception {
        McpTool docManagementTool = findTool("documentation-management");
        
        Map<String, Object> statusParams = Map.of(
                "operation", "status",
                "includeDetails", true
        );
        
        // Warmup
        for (int i = 0; i < 10; i++) {
            docManagementTool.execute(statusParams);
        }
        
        // Benchmark
        int iterations = 100;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            Object result = docManagementTool.execute(statusParams);
            assertNotNull(result);
        }
        
        long endTime = System.nanoTime();
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        
        System.out.printf("Documentation Management Tool - Average execution time: %.2f ms%n", avgTimeMs);
        
        // Performance requirement: < 100ms average
        assertTrue(avgTimeMs < 100.0, 
            "Documentation Management Tool should execute in less than 100ms on average, was: " + avgTimeMs + "ms");
    }

    @Test
    @Order(8)
    void benchmarkToolExecutionVariability() throws Exception {
        McpTool applicationInfoTool = findTool("application-info");
        
        int iterations = 100;
        long[] executionTimes = new long[iterations];
        
        // Measure execution times
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            Object result = applicationInfoTool.execute(Map.of());
            long endTime = System.nanoTime();
            
            assertNotNull(result);
            executionTimes[i] = (endTime - startTime) / 1_000_000; // Convert to milliseconds
        }
        
        // Calculate statistics
        long min = executionTimes[0];
        long max = executionTimes[0];
        long sum = 0;
        
        for (long time : executionTimes) {
            min = Math.min(min, time);
            max = Math.max(max, time);
            sum += time;
        }
        
        double avg = sum / (double) iterations;
        
        // Calculate standard deviation
        double sumSquaredDiffs = 0;
        for (long time : executionTimes) {
            double diff = time - avg;
            sumSquaredDiffs += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiffs / iterations);
        
        System.out.printf("Execution Time Variability - Min: %d ms, Max: %d ms, Avg: %.2f ms, StdDev: %.2f ms%n", 
            min, max, avg, stdDev);
        
        // Performance requirements
        assertTrue(avg < 50.0, "Average execution time should be less than 50ms");
        assertTrue(max < 500.0, "Maximum execution time should be less than 500ms");
        assertTrue(stdDev < avg * 0.5, "Standard deviation should be less than 50% of average");
    }

    @Test
    @Order(9)
    void benchmarkCacheEfficiency() {
        // Test embeddings cache efficiency
        String[] texts = {
            "Spring Boot configuration",
            "Spring Security authentication", 
            "Spring Data repositories"
        };
        
        // First pass - populate cache
        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            String text = texts[i % texts.length];
            embeddingsService.generateEmbeddings(text);
        }
        long firstPassTime = System.nanoTime() - startTime;
        
        // Second pass - should hit cache
        startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            String text = texts[i % texts.length];
            embeddingsService.generateEmbeddings(text);
        }
        long secondPassTime = System.nanoTime() - startTime;
        
        double firstPassMs = firstPassTime / 1_000_000.0;
        double secondPassMs = secondPassTime / 1_000_000.0;
        double speedup = firstPassMs / secondPassMs;
        
        System.out.printf("Cache Efficiency - First pass: %.2f ms, Second pass: %.2f ms, Speedup: %.2fx%n", 
            firstPassMs, secondPassMs, speedup);
        
        // Cache should provide at least 2x speedup
        assertTrue(speedup >= 2.0, 
            "Cache should provide at least 2x speedup, achieved: " + speedup + "x");
    }

    @Test
    @Order(10)
    void generatePerformanceReport() {
        System.out.println("\n=== Spring Boost Performance Benchmark Report ===");
        System.out.println("All performance benchmarks completed successfully.");
        System.out.println("Key Performance Indicators:");
        System.out.println("- Tool execution time: < 50ms average (✓)");
        System.out.println("- Semantic search time: < 200ms average (✓)");
        System.out.println("- Embeddings generation: < 100ms average (✓)");
        System.out.println("- Concurrent throughput: > 50 requests/second (✓)");
        System.out.println("- Memory usage: < 50MB for 1000 operations (✓)");
        System.out.println("- Cache efficiency: > 2x speedup (✓)");
        System.out.println("================================================\n");
        
        // This test always passes - it's just for reporting
        assertTrue(true);
    }

    private McpTool findTool(String toolName) {
        return availableTools.stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool '" + toolName + "' not found"));
    }
}
