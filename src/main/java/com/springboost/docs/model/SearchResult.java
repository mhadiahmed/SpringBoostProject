package com.springboost.docs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Search result containing ranked documentation chunks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    
    private String query;
    private List<DocumentChunk> results;
    private int totalResults;
    private long searchTimeMs;
    private String searchType; // "semantic", "keyword", "hybrid"
    
    @Builder.Default
    private int page = 1;
    
    @Builder.Default
    private int pageSize = 10;
    
    /**
     * Get the top result
     */
    public DocumentChunk getTopResult() {
        return results != null && !results.isEmpty() ? results.get(0) : null;
    }
    
    /**
     * Check if search has results
     */
    public boolean hasResults() {
        return results != null && !results.isEmpty();
    }
    
    /**
     * Get results with relevance score above threshold
     */
    public List<DocumentChunk> getRelevantResults(double minScore) {
        if (results == null) {
            return List.of();
        }
        
        return results.stream()
                .filter(chunk -> chunk.getRelevanceScore() >= minScore)
                .toList();
    }
    
    /**
     * Get results from specific source
     */
    public List<DocumentChunk> getResultsFromSource(String source) {
        if (results == null) {
            return List.of();
        }
        
        return results.stream()
                .filter(chunk -> source.equals(chunk.getSource()))
                .toList();
    }
    
    /**
     * Create an empty search result
     */
    public static SearchResult empty(String query) {
        return SearchResult.builder()
                .query(query)
                .results(List.of())
                .totalResults(0)
                .searchTimeMs(0)
                .build();
    }
}
