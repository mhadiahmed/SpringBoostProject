package com.springboost.docs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request object for documentation search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {
    
    private String query;
    private String source; // Filter by documentation source
    private String version; // Filter by version
    private String category; // Filter by category
    private List<String> tags; // Filter by tags
    
    @Builder.Default
    private int maxResults = 10;
    
    @Builder.Default
    private double minRelevanceScore = 0.0;
    
    @Builder.Default
    private boolean includeCodeSnippets = true;
    
    @Builder.Default
    private boolean includeMetadata = false;
    
    // Search options
    @Builder.Default
    private boolean semanticSearch = true;
    
    @Builder.Default
    private boolean keywordSearch = false;
    
    @Builder.Default
    private boolean fuzzySearch = false;
    
    /**
     * Create a simple search request
     */
    public static SearchRequest simple(String query) {
        return SearchRequest.builder()
                .query(query)
                .build();
    }
    
    /**
     * Create a search request with filters
     */
    public static SearchRequest filtered(String query, String source, String version) {
        return SearchRequest.builder()
                .query(query)
                .source(source)
                .version(version)
                .build();
    }
    
    /**
     * Create a search request for code examples
     */
    public static SearchRequest forCodeExamples(String query) {
        return SearchRequest.builder()
                .query(query)
                .includeCodeSnippets(true)
                .category("examples")
                .build();
    }
    
    /**
     * Validate the search request
     */
    public boolean isValid() {
        return query != null && !query.trim().isEmpty() && maxResults > 0;
    }
}
