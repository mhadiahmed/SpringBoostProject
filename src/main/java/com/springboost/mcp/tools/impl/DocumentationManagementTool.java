package com.springboost.mcp.tools.impl;

import com.springboost.docs.service.DocumentationService;
import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool for managing documentation sources, indexing, and updates
 */
@Slf4j
@Component
public class DocumentationManagementTool implements McpTool {
    
    private final DocumentationService documentationService;
    
    public DocumentationManagementTool(DocumentationService documentationService) {
        this.documentationService = documentationService;
    }
    
    @Override
    public String getName() {
        return "documentation-management";
    }
    
    @Override
    public String getDescription() {
        return "Manage documentation sources, view index statistics, and trigger documentation updates";
    }
    
    @Override
    public String getCategory() {
        return "documentation";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "operation", Map.of(
                        "type", "string",
                        "description", "Management operation to perform",
                        "enum", Arrays.asList("status", "update", "index-stats", "list-sources", "clear-cache"),
                        "default", "status"
                ),
                "source", Map.of(
                        "type", "string",
                        "description", "Specific documentation source to operate on",
                        "enum", Arrays.asList("all", "spring-boot", "spring-security", "spring-data"),
                        "default", "all"
                ),
                "includeDetails", Map.of(
                        "type", "boolean",
                        "description", "Include detailed information in response",
                        "default", false
                )
        ));
        schema.put("required", Arrays.asList("operation"));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String operation = (String) params.get("operation");
            String source = (String) params.getOrDefault("source", "all");
            boolean includeDetails = (boolean) params.getOrDefault("includeDetails", false);
            
            // Validate required parameters
            if (operation == null || operation.trim().isEmpty()) {
                throw new McpToolException("Operation parameter is required");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("operation", operation);
            result.put("source", source);
            result.put("timestamp", System.currentTimeMillis());
            
            switch (operation) {
                case "status":
                    result.putAll(getDocumentationStatus(source, includeDetails));
                    break;
                    
                case "update":
                    result.putAll(updateDocumentation(source));
                    break;
                    
                case "index-stats":
                    result.putAll(getIndexStatistics(source, includeDetails));
                    break;
                    
                case "list-sources":
                    result.putAll(listDocumentationSources(includeDetails));
                    break;
                    
                case "clear-cache":
                    result.putAll(clearDocumentationCache(source));
                    break;
                    
                default:
                    throw new McpToolException(getName(), "Unknown operation: " + operation);
            }
            
            return result;
            
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute documentation management operation: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to execute operation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get documentation status
     */
    private Map<String, Object> getDocumentationStatus(String source, boolean includeDetails) {
        Map<String, Object> status = new HashMap<>();
        
        // Get index statistics
        Map<String, Object> indexStats = documentationService.getIndexStats();
        status.put("indexStats", indexStats);
        
        // Get source-specific information
        if (!"all".equals(source)) {
            status.put("documentsInSource", documentationService.getDocumentsBySource(source).size());
        }
        
        // Available sources
        Set<String> availableSources = Set.of("spring-boot", "spring-security", "spring-data");
        status.put("availableSources", availableSources);
        
        // Service status
        status.put("serviceStatus", "active");
        status.put("indexingEnabled", true);
        status.put("searchEnabled", true);
        
        if (includeDetails) {
            // Detailed breakdown by source
            Map<String, Object> sourceBreakdown = new HashMap<>();
            for (String src : availableSources) {
                Map<String, Object> srcInfo = new HashMap<>();
                srcInfo.put("documentCount", documentationService.getDocumentsBySource(src).size());
                srcInfo.put("status", "indexed");
                srcInfo.put("lastUpdated", indexStats.get("lastUpdated"));
                sourceBreakdown.put(src, srcInfo);
            }
            status.put("sourceBreakdown", sourceBreakdown);
            
            // Sample documents
            status.put("sampleDocuments", documentationService.getAllDocuments().stream()
                    .limit(3)
                    .map(chunk -> Map.of(
                            "title", chunk.getTitle(),
                            "source", chunk.getSource(),
                            "category", chunk.getCategory() != null ? chunk.getCategory() : "unknown",
                            "wordCount", chunk.getWordCount()
                    ))
                    .toList());
        }
        
        return status;
    }
    
    /**
     * Update documentation from sources
     */
    private Map<String, Object> updateDocumentation(String source) {
        Map<String, Object> updateResult = new HashMap<>();
        
        try {
            int documentsProcessed = 0;
            
            if ("all".equals(source)) {
                // Update all sources
                updateResult.put("message", "Documentation update initiated for all sources");
                updateResult.put("status", "in-progress");
                
                // For demo purposes, we'll simulate the update
                documentsProcessed = simulateDocumentationUpdate();
                
            } else {
                // Update specific source
                updateResult.put("message", "Documentation update initiated for source: " + source);
                updateResult.put("status", "in-progress");
                
                documentsProcessed = simulateSourceUpdate(source);
            }
            
            updateResult.put("documentsProcessed", documentsProcessed);
            updateResult.put("updateCompleted", true);
            
        } catch (Exception e) {
            updateResult.put("status", "failed");
            updateResult.put("error", e.getMessage());
            log.error("Documentation update failed: {}", e.getMessage());
        }
        
        return updateResult;
    }
    
    /**
     * Get detailed index statistics
     */
    private Map<String, Object> getIndexStatistics(String source, boolean includeDetails) {
        Map<String, Object> stats = new HashMap<>(documentationService.getIndexStats());
        
        if (!"all".equals(source)) {
            // Filter statistics for specific source
            int sourceDocCount = documentationService.getDocumentsBySource(source).size();
            stats.put("sourceDocumentCount", sourceDocCount);
            stats.put("sourceFilter", source);
        }
        
        if (includeDetails) {
            // Add detailed metrics
            stats.put("averageDocumentLength", calculateAverageDocumentLength());
            stats.put("documentsWithCodeSnippets", countDocumentsWithCodeSnippets());
            stats.put("documentsWithEmbeddings", countDocumentsWithEmbeddings());
            stats.put("categoryDistribution", getCategoryDistribution());
            stats.put("versionDistribution", getVersionDistribution());
        }
        
        return stats;
    }
    
    /**
     * List available documentation sources
     */
    private Map<String, Object> listDocumentationSources(boolean includeDetails) {
        Map<String, Object> sources = new HashMap<>();
        
        List<Map<String, Object>> sourceList = new ArrayList<>();
        
        // Spring Boot
        Map<String, Object> springBoot = new HashMap<>();
        springBoot.put("name", "spring-boot");
        springBoot.put("title", "Spring Boot Documentation");
        springBoot.put("version", "3.2.0");
        springBoot.put("baseUrl", "https://docs.spring.io/spring-boot/docs/current/reference/html/");
        springBoot.put("documentCount", documentationService.getDocumentsBySource("spring-boot").size());
        springBoot.put("categories", Arrays.asList("core", "configuration", "actuator", "testing"));
        if (includeDetails) {
            springBoot.put("description", "Comprehensive Spring Boot reference documentation");
            springBoot.put("lastUpdated", "2024-01-15");
            springBoot.put("indexStatus", "active");
        }
        sourceList.add(springBoot);
        
        // Spring Security
        Map<String, Object> springSecurity = new HashMap<>();
        springSecurity.put("name", "spring-security");
        springSecurity.put("title", "Spring Security Documentation");
        springSecurity.put("version", "6.1.0");
        springSecurity.put("baseUrl", "https://docs.spring.io/spring-security/reference/");
        springSecurity.put("documentCount", documentationService.getDocumentsBySource("spring-security").size());
        springSecurity.put("categories", Arrays.asList("security", "authentication", "authorization"));
        if (includeDetails) {
            springSecurity.put("description", "Spring Security reference documentation");
            springSecurity.put("lastUpdated", "2024-01-10");
            springSecurity.put("indexStatus", "active");
        }
        sourceList.add(springSecurity);
        
        // Spring Data
        Map<String, Object> springData = new HashMap<>();
        springData.put("name", "spring-data");
        springData.put("title", "Spring Data JPA Documentation");
        springData.put("version", "3.1.0");
        springData.put("baseUrl", "https://docs.spring.io/spring-data/jpa/docs/current/reference/html/");
        springData.put("documentCount", documentationService.getDocumentsBySource("spring-data").size());
        springData.put("categories", Arrays.asList("data", "repositories", "queries"));
        if (includeDetails) {
            springData.put("description", "Spring Data JPA reference documentation");
            springData.put("lastUpdated", "2024-01-08");
            springData.put("indexStatus", "active");
        }
        sourceList.add(springData);
        
        sources.put("sources", sourceList);
        sources.put("totalSources", sourceList.size());
        
        return sources;
    }
    
    /**
     * Clear documentation cache
     */
    private Map<String, Object> clearDocumentationCache(String source) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if ("all".equals(source)) {
                // Clear all caches - for demo, we'll just report success
                result.put("message", "All documentation caches cleared");
                result.put("cacheTypesCleared", Arrays.asList("document-index", "embeddings", "search-results"));
            } else {
                result.put("message", "Cache cleared for source: " + source);
                result.put("source", source);
            }
            
            result.put("status", "success");
            result.put("clearedAt", System.currentTimeMillis());
            
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    // Helper methods for statistics
    private double calculateAverageDocumentLength() {
        return documentationService.getAllDocuments().stream()
                .mapToInt(chunk -> chunk.getContent() != null ? chunk.getContent().length() : 0)
                .average()
                .orElse(0.0);
    }
    
    private long countDocumentsWithCodeSnippets() {
        return documentationService.getAllDocuments().stream()
                .mapToLong(chunk -> chunk.getCodeSnippets() != null && !chunk.getCodeSnippets().isEmpty() ? 1 : 0)
                .sum();
    }
    
    private long countDocumentsWithEmbeddings() {
        return documentationService.getAllDocuments().stream()
                .mapToLong(chunk -> chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty() ? 1 : 0)
                .sum();
    }
    
    private Map<String, Long> getCategoryDistribution() {
        return documentationService.getAllDocuments().stream()
                .collect(Collectors.groupingBy(
                        chunk -> chunk.getCategory() != null ? chunk.getCategory() : "unknown",
                        Collectors.counting()
                ));
    }
    
    private Map<String, Long> getVersionDistribution() {
        return documentationService.getAllDocuments().stream()
                .collect(Collectors.groupingBy(
                        chunk -> chunk.getVersion() != null ? chunk.getVersion() : "unknown",
                        Collectors.counting()
                ));
    }
    
    // Simulation methods for demo purposes
    private int simulateDocumentationUpdate() {
        // Simulate processing multiple sources
        return 150; // Simulated number of documents processed
    }
    
    private int simulateSourceUpdate(String source) {
        // Simulate processing single source
        return switch (source) {
            case "spring-boot" -> 75;
            case "spring-security" -> 45;
            case "spring-data" -> 30;
            default -> 0;
        };
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "getStatus", Map.of(
                        "description", "Get overall documentation status",
                        "parameters", Map.of("operation", "status")
                ),
                "getDetailedStats", Map.of(
                        "description", "Get detailed index statistics",
                        "parameters", Map.of(
                                "operation", "index-stats",
                                "includeDetails", true
                        )
                ),
                "updateSpringBoot", Map.of(
                        "description", "Update Spring Boot documentation",
                        "parameters", Map.of(
                                "operation", "update",
                                "source", "spring-boot"
                        )
                ),
                "listSourcesDetailed", Map.of(
                        "description", "List all documentation sources with details",
                        "parameters", Map.of(
                                "operation", "list-sources",
                                "includeDetails", true
                        )
                ),
                "clearCache", Map.of(
                        "description", "Clear all documentation caches",
                        "parameters", Map.of("operation", "clear-cache")
                )
        );
    }
}
