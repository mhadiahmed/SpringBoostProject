package com.springboost.mcp.tools.impl;

import com.springboost.docs.model.DocumentChunk;
import com.springboost.docs.model.SearchRequest;
import com.springboost.docs.model.SearchResult;
import com.springboost.docs.service.SearchService;
import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool to search Spring documentation with semantic search capabilities
 * Provides context-aware documentation retrieval with version-specific results
 */
@Slf4j
@Component
public class SearchDocsTool implements McpTool {
    
    private final SearchService searchService;
    
    public SearchDocsTool(SearchService searchService) {
        this.searchService = searchService;
    }
    
    @Override
    public String getName() {
        return "search-docs";
    }
    
    @Override
    public String getDescription() {
        return "Search Spring documentation with semantic search across Spring Boot, Security, Data, and Framework docs";
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
                "query", Map.of(
                        "type", "string",
                        "description", "Search query (e.g., 'spring security jwt', 'actuator endpoints', 'jpa repositories')",
                        "minLength", 2
                ),
                "source", Map.of(
                        "type", "string",
                        "description", "Documentation source to search",
                        "enum", Arrays.asList("all", "spring-boot", "spring-security", "spring-data"),
                        "default", "all"
                ),
                "version", Map.of(
                        "type", "string",
                        "description", "Documentation version to search",
                        "enum", Arrays.asList("3.2.0", "6.1.0", "3.1.0"),
                        "default", null
                ),
                "category", Map.of(
                        "type", "string",
                        "description", "Documentation category to search",
                        "enum", Arrays.asList("core", "security", "data", "web", "testing", "configuration"),
                        "default", null
                ),
                "includeCode", Map.of(
                        "type", "boolean",
                        "description", "Include code examples in results",
                        "default", true
                ),
                "maxResults", Map.of(
                        "type", "integer",
                        "description", "Maximum number of results to return",
                        "default", 5,
                        "minimum", 1,
                        "maximum", 20
                ),
                "semanticSearch", Map.of(
                        "type", "boolean",
                        "description", "Enable semantic search using embeddings",
                        "default", true
                ),
                "keywordSearch", Map.of(
                        "type", "boolean", 
                        "description", "Enable keyword-based search",
                        "default", false
                ),
                "format", Map.of(
                        "type", "string",
                        "description", "Result format",
                        "enum", Arrays.asList("full", "summary", "links-only"),
                        "default", "full"
                )
        ));
        schema.put("required", Arrays.asList("query"));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String query = (String) params.get("query");
            if (query == null || query.trim().isEmpty()) {
                throw new McpToolException(getName(), "Query parameter is required and cannot be empty");
            }
            
            String source = (String) params.getOrDefault("source", "all");
            String version = (String) params.get("version");
            String category = (String) params.get("category");
            boolean includeCode = (boolean) params.getOrDefault("includeCode", true);
            int maxResults = ((Number) params.getOrDefault("maxResults", 5)).intValue();
            boolean semanticSearch = (boolean) params.getOrDefault("semanticSearch", true);
            boolean keywordSearch = (boolean) params.getOrDefault("keywordSearch", false);
            String format = (String) params.getOrDefault("format", "full");
            
            // Build search request
            SearchRequest.SearchRequestBuilder requestBuilder = SearchRequest.builder()
                    .query(query)
                    .maxResults(maxResults)
                    .includeCodeSnippets(includeCode)
                    .semanticSearch(semanticSearch)
                    .keywordSearch(keywordSearch);
            
            // Apply filters
            if (!"all".equals(source)) {
                requestBuilder.source(source);
            }
            if (version != null) {
                requestBuilder.version(version);
            }
            if (category != null) {
                requestBuilder.category(category);
            }
            
            SearchRequest searchRequest = requestBuilder.build();
            
            // Perform search using the SearchService
            SearchResult searchResult = searchService.search(searchRequest);
            
            // Format the response
            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("source", source);
            result.put("version", version);
            result.put("category", category);
            result.put("timestamp", System.currentTimeMillis());
            result.put("searchType", searchResult.getSearchType());
            result.put("searchTimeMs", searchResult.getSearchTimeMs());
            
            // Format results based on requested format
            result.put("results", formatSearchResults(searchResult.getResults(), format, includeCode));
            result.put("resultCount", searchResult.getTotalResults());
            
            // Add search suggestions if few results
            if (searchResult.getTotalResults() < 3) {
                result.put("suggestions", searchService.getSearchSuggestions(query, 5));
            }
            
            // Add similar documents for top result
            if (searchResult.hasResults()) {
                DocumentChunk topResult = searchResult.getTopResult();
                List<DocumentChunk> similar = searchService.findSimilarDocuments(topResult.getId(), 3);
                result.put("similarDocuments", formatSimilarDocuments(similar));
            }
            
            // Add search statistics
            result.put("searchStats", searchService.getSearchStats());
            
            return result;
            
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to search documentation: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to search documentation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Format search results based on the requested format
     */
    private List<Map<String, Object>> formatSearchResults(List<DocumentChunk> results, String format, boolean includeCode) {
        return results.stream().map(chunk -> {
            Map<String, Object> formatted = new HashMap<>();
            
            switch (format) {
                case "summary":
                    formatted.put("title", chunk.getTitle());
                    formatted.put("summary", chunk.getContentPreview(150));
                    formatted.put("url", chunk.getUrl());
                    formatted.put("source", chunk.getSource());
                    formatted.put("version", chunk.getVersion());
                    formatted.put("relevanceScore", chunk.getRelevanceScore());
                    break;
                    
                case "links-only":
                    formatted.put("title", chunk.getTitle());
                    formatted.put("url", chunk.getUrl());
                    formatted.put("source", chunk.getSource());
                    formatted.put("relevanceScore", chunk.getRelevanceScore());
                    break;
                    
                case "full":
                default:
                    formatted.put("title", chunk.getTitle());
                    formatted.put("content", chunk.getContent());
                    formatted.put("url", chunk.getUrl());
                    formatted.put("source", chunk.getSource());
                    formatted.put("version", chunk.getVersion());
                    formatted.put("category", chunk.getCategory());
                    formatted.put("tags", chunk.getTags());
                    formatted.put("wordCount", chunk.getWordCount());
                    formatted.put("relevanceScore", chunk.getRelevanceScore());
                    formatted.put("createdAt", chunk.getCreatedAt());
                    formatted.put("updatedAt", chunk.getUpdatedAt());
                    
                    if (includeCode && chunk.getCodeSnippets() != null && !chunk.getCodeSnippets().isEmpty()) {
                        formatted.put("codeSnippets", chunk.getCodeSnippets());
                    }
                    
                    if (chunk.getConfigurationExamples() != null && !chunk.getConfigurationExamples().isEmpty()) {
                        formatted.put("configurationExamples", chunk.getConfigurationExamples());
                    }
                    
                    if (chunk.getMetadata() != null && !chunk.getMetadata().isEmpty()) {
                        formatted.put("metadata", chunk.getMetadata());
                    }
                    break;
            }
            
            return formatted;
        }).collect(Collectors.toList());
    }
    
    /**
     * Format similar documents for display
     */
    private List<Map<String, Object>> formatSimilarDocuments(List<DocumentChunk> similarDocs) {
        return similarDocs.stream().map(chunk -> {
            Map<String, Object> formatted = new HashMap<>();
            formatted.put("title", chunk.getTitle());
            formatted.put("url", chunk.getUrl());
            formatted.put("source", chunk.getSource());
            formatted.put("category", chunk.getCategory() != null ? chunk.getCategory() : "unknown");
            formatted.put("similarityScore", chunk.getRelevanceScore());
            return formatted;
        }).collect(Collectors.toList());
    }
    
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "semanticSecuritySearch", Map.of(
                        "description", "Semantic search for JWT authentication documentation",
                        "parameters", Map.of(
                                "query", "spring security jwt authentication",
                                "semanticSearch", true
                        )
                ),
                "filteredActuatorSearch", Map.of(
                        "description", "Find actuator endpoint documentation in Spring Boot only",
                        "parameters", Map.of(
                                "query", "actuator endpoints monitoring",
                                "source", "spring-boot",
                                "category", "core",
                                "version", "3.2.0"
                        )
                ),
                "codeExampleSearch", Map.of(
                        "description", "Search for JPA repository code examples",
                        "parameters", Map.of(
                                "query", "jpa repositories query methods",
                                "source", "spring-data",
                                "includeCode", true,
                                "semanticSearch", true,
                                "keywordSearch", true
                        )
                ),
                "summarySearch", Map.of(
                        "description", "Quick summary search for testing configuration",
                        "parameters", Map.of(
                                "query", "spring boot testing configuration",
                                "maxResults", 3,
                                "format", "summary",
                                "category", "testing"
                        )
                ),
                "hybridConfigSearch", Map.of(
                        "description", "Hybrid search for configuration properties",
                        "parameters", Map.of(
                                "query", "configuration properties externalized config",
                                "semanticSearch", true,
                                "keywordSearch", true,
                                "maxResults", 10
                        )
                )
        );
    }
}
