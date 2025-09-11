package com.springboost.monitoring;

import com.springboost.docs.service.DocumentationService;
import com.springboost.docs.service.EmbeddingsService;
import com.springboost.docs.service.SearchService;
import com.springboost.mcp.error.McpErrorHandler;
import com.springboost.mcp.tools.McpToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Health monitor for Spring Boost components
 * Provides detailed health checks and system status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthMonitor {
    
    private final McpToolRegistry toolRegistry;
    private final DocumentationService documentationService;
    private final SearchService searchService;
    private final EmbeddingsService embeddingsService;
    private final McpErrorHandler errorHandler;
    
    /**
     * Get overall health status
     */
    public Map<String, Object> getHealthStatus() {
        try {
            Map<String, Object> healthStatus = new ConcurrentHashMap<>();
            boolean overallHealthy = true;
            
            // Check MCP Tools
            Map<String, Object> toolsHealth = checkToolsHealth();
            healthStatus.put("tools", toolsHealth);
            if (!(Boolean) toolsHealth.get("healthy")) {
                overallHealthy = false;
            }
            
            // Check Documentation Service
            Map<String, Object> docsHealth = checkDocumentationServiceHealth();
            healthStatus.put("documentation", docsHealth);
            if (!(Boolean) docsHealth.get("healthy")) {
                overallHealthy = false;
            }
            
            // Check Search Service
            Map<String, Object> searchHealth = checkSearchServiceHealth();
            healthStatus.put("search", searchHealth);
            if (!(Boolean) searchHealth.get("healthy")) {
                overallHealthy = false;
            }
            
            // Check Embeddings Service
            Map<String, Object> embeddingsHealth = checkEmbeddingsServiceHealth();
            healthStatus.put("embeddings", embeddingsHealth);
            if (!(Boolean) embeddingsHealth.get("healthy")) {
                overallHealthy = false;
            }
            
            // Check Error Handler Status
            Map<String, Object> errorStats = errorHandler.getComponentHealthStatus();
            healthStatus.put("errorHandler", errorStats);
            
            // System Information
            healthStatus.put("system", getSystemInformation());
            
            // Overall status
            healthStatus.put("status", overallHealthy ? "UP" : "DOWN");
            healthStatus.put("healthy", overallHealthy);
            healthStatus.put("timestamp", System.currentTimeMillis());
            
            return healthStatus;
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Map.of(
                    "status", "ERROR",
                    "healthy", false,
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            );
        }
    }
    
    /**
     * Check MCP tools health
     */
    private Map<String, Object> checkToolsHealth() {
        Map<String, Object> toolsHealth = new ConcurrentHashMap<>();
        
        try {
            int totalTools = toolRegistry.getAllTools().size();
            int healthyTools = 0;
            
            // Test a sample of tools
            String[] criticalTools = {
                "application-info", "search-docs", "documentation-management"
            };
            
            for (String toolName : criticalTools) {
                try {
                    boolean healthy = errorHandler.isComponentHealthy("tool:" + toolName);
                    if (healthy) {
                        healthyTools++;
                    }
                } catch (Exception e) {
                    log.warn("Health check failed for tool: {}", toolName, e);
                }
            }
            
            boolean overallHealthy = healthyTools == criticalTools.length;
            
            toolsHealth.put("healthy", overallHealthy);
            toolsHealth.put("totalTools", totalTools);
            toolsHealth.put("healthyTools", healthyTools);
            toolsHealth.put("criticalToolsChecked", criticalTools.length);
            toolsHealth.put("status", overallHealthy ? "UP" : "DOWN");
            
        } catch (Exception e) {
            toolsHealth.put("healthy", false);
            toolsHealth.put("error", e.getMessage());
            toolsHealth.put("status", "ERROR");
        }
        
        return toolsHealth;
    }
    
    /**
     * Check documentation service health
     */
    private Map<String, Object> checkDocumentationServiceHealth() {
        Map<String, Object> docsHealth = new ConcurrentHashMap<>();
        
        try {
            // Test basic functionality
            Map<String, Object> indexStats = documentationService.getIndexStats();
            int documentCount = (Integer) indexStats.get("totalDocuments");
            
            boolean healthy = documentCount > 0 && 
                            errorHandler.isComponentHealthy("documentation");
            
            docsHealth.put("healthy", healthy);
            docsHealth.put("documentCount", documentCount);
            docsHealth.put("indexStats", indexStats);
            docsHealth.put("status", healthy ? "UP" : "DOWN");
            
        } catch (Exception e) {
            docsHealth.put("healthy", false);
            docsHealth.put("error", e.getMessage());
            docsHealth.put("status", "ERROR");
            log.warn("Documentation service health check failed", e);
        }
        
        return docsHealth;
    }
    
    /**
     * Check search service health
     */
    private Map<String, Object> checkSearchServiceHealth() {
        Map<String, Object> searchHealth = new ConcurrentHashMap<>();
        
        try {
            // Test search functionality
            long startTime = System.currentTimeMillis();
            Map<String, Object> searchStats = searchService.getSearchStats();
            long responseTime = System.currentTimeMillis() - startTime;
            
            boolean healthy = responseTime < 1000 && 
                            errorHandler.isComponentHealthy("search") &&
                            searchStats != null;
            
            searchHealth.put("healthy", healthy);
            searchHealth.put("responseTimeMs", responseTime);
            searchHealth.put("searchStats", searchStats);
            searchHealth.put("status", healthy ? "UP" : "DOWN");
            
        } catch (Exception e) {
            searchHealth.put("healthy", false);
            searchHealth.put("error", e.getMessage());
            searchHealth.put("status", "ERROR");
            log.warn("Search service health check failed", e);
        }
        
        return searchHealth;
    }
    
    /**
     * Check embeddings service health
     */
    private Map<String, Object> checkEmbeddingsServiceHealth() {
        Map<String, Object> embeddingsHealth = new ConcurrentHashMap<>();
        
        try {
            // Test embeddings generation
            long startTime = System.currentTimeMillis();
            var embeddings = embeddingsService.generateEmbeddings("health check test");
            long responseTime = System.currentTimeMillis() - startTime;
            
            boolean healthy = embeddings != null && 
                            !embeddings.isEmpty() && 
                            responseTime < 1000 &&
                            errorHandler.isComponentHealthy("embeddings");
            
            Map<String, Object> cacheStats = embeddingsService.getCacheStats();
            
            embeddingsHealth.put("healthy", healthy);
            embeddingsHealth.put("responseTimeMs", responseTime);
            embeddingsHealth.put("embeddingDimension", embeddings != null ? embeddings.size() : 0);
            embeddingsHealth.put("cacheStats", cacheStats);
            embeddingsHealth.put("status", healthy ? "UP" : "DOWN");
            
        } catch (Exception e) {
            embeddingsHealth.put("healthy", false);
            embeddingsHealth.put("error", e.getMessage());
            embeddingsHealth.put("status", "ERROR");
            log.warn("Embeddings service health check failed", e);
        }
        
        return embeddingsHealth;
    }
    
    /**
     * Get system information
     */
    private Map<String, Object> getSystemInformation() {
        Runtime runtime = Runtime.getRuntime();
        
        return Map.of(
                "javaVersion", System.getProperty("java.version"),
                "springBootVersion", getClass().getPackage().getImplementationVersion(),
                "totalMemoryMB", runtime.totalMemory() / (1024 * 1024),
                "freeMemoryMB", runtime.freeMemory() / (1024 * 1024),
                "usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                "maxMemoryMB", runtime.maxMemory() / (1024 * 1024),
                "availableProcessors", runtime.availableProcessors(),
                "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Perform detailed health check with diagnostics
     */
    public Map<String, Object> performDetailedHealthCheck() {
        Map<String, Object> detailedHealth = new ConcurrentHashMap<>();
        
        try {
            // Basic health check
            Map<String, Object> basicHealth = getHealthStatus();
            detailedHealth.put("basicHealth", basicHealth.get("status"));
            detailedHealth.put("basicDetails", basicHealth);
            
            // Performance metrics
            detailedHealth.put("performance", gatherPerformanceMetrics());
            
            // Error statistics
            detailedHealth.put("errorStatistics", errorHandler.getErrorStatistics());
            
            // Component diagnostics
            detailedHealth.put("diagnostics", performComponentDiagnostics());
            
        } catch (Exception e) {
            detailedHealth.put("error", "Failed to perform detailed health check: " + e.getMessage());
            log.error("Detailed health check failed", e);
        }
        
        return detailedHealth;
    }
    
    /**
     * Gather performance metrics
     */
    private Map<String, Object> gatherPerformanceMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        
        try {
            // Test application info tool performance
            long startTime = System.currentTimeMillis();
            toolRegistry.getAllTools().stream()
                    .filter(tool -> "application-info".equals(tool.getName()))
                    .findFirst()
                    .ifPresent(tool -> {
                        try {
                            tool.execute(Map.of());
                        } catch (Exception e) {
                            log.debug("Performance test failed for application-info tool", e);
                        }
                    });
            long toolResponseTime = System.currentTimeMillis() - startTime;
            
            // Test search performance
            startTime = System.currentTimeMillis();
            searchService.getSearchStats();
            long searchResponseTime = System.currentTimeMillis() - startTime;
            
            // Test embeddings performance
            startTime = System.currentTimeMillis();
            embeddingsService.generateEmbeddings("performance test");
            long embeddingsResponseTime = System.currentTimeMillis() - startTime;
            
            metrics.put("toolResponseTimeMs", toolResponseTime);
            metrics.put("searchResponseTimeMs", searchResponseTime);
            metrics.put("embeddingsResponseTimeMs", embeddingsResponseTime);
            metrics.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            metrics.put("error", "Failed to gather performance metrics: " + e.getMessage());
        }
        
        return metrics;
    }
    
    /**
     * Perform component diagnostics
     */
    private Map<String, Object> performComponentDiagnostics() {
        Map<String, Object> diagnostics = new ConcurrentHashMap<>();
        
        try {
            // Tool registry diagnostics
            diagnostics.put("toolRegistry", Map.of(
                    "toolCount", toolRegistry.getAllTools().size(),
                    "toolNames", toolRegistry.getAllTools().stream()
                            .map(tool -> tool.getName())
                            .toList()
            ));
            
            // Documentation service diagnostics
            Map<String, Object> docStats = documentationService.getIndexStats();
            diagnostics.put("documentationService", docStats);
            
            // Search service diagnostics
            Map<String, Object> searchStats = searchService.getSearchStats();
            diagnostics.put("searchService", searchStats);
            
            // Embeddings service diagnostics
            Map<String, Object> embeddingsStats = embeddingsService.getCacheStats();
            diagnostics.put("embeddingsService", embeddingsStats);
            
        } catch (Exception e) {
            diagnostics.put("error", "Failed to perform component diagnostics: " + e.getMessage());
        }
        
        return diagnostics;
    }
}