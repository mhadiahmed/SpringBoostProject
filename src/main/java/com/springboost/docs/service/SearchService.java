package com.springboost.docs.service;

import com.springboost.docs.model.DocumentChunk;
import com.springboost.docs.model.SearchRequest;
import com.springboost.docs.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for performing semantic and keyword-based search across documentation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    
    private final DocumentationService documentationService;
    private final EmbeddingsService embeddingsService;
    
    /**
     * Perform a search based on the search request
     */
    public SearchResult search(SearchRequest request) {
        if (!request.isValid()) {
            return SearchResult.empty(request.getQuery());
        }
        
        long startTime = System.currentTimeMillis();
        
        List<DocumentChunk> results = new ArrayList<>();
        String searchType = "hybrid";
        
        try {
            if (request.isSemanticSearch()) {
                results.addAll(performSemanticSearch(request));
                searchType = "semantic";
            }
            
            if (request.isKeywordSearch()) {
                List<DocumentChunk> keywordResults = performKeywordSearch(request);
                results.addAll(keywordResults);
                searchType = request.isSemanticSearch() ? "hybrid" : "keyword";
            }
            
            // If no specific search type is enabled, default to semantic
            if (!request.isSemanticSearch() && !request.isKeywordSearch()) {
                results.addAll(performSemanticSearch(request));
                searchType = "semantic";
            }
            
            // Remove duplicates and filter
            results = deduplicateAndFilter(results, request);
            
            // Sort by relevance score
            results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
            
            // Limit results
            if (results.size() > request.getMaxResults()) {
                results = results.subList(0, request.getMaxResults());
            }
            
        } catch (Exception e) {
            log.error("Search failed for query '{}': {}", request.getQuery(), e.getMessage());
            results = new ArrayList<>();
        }
        
        long searchTime = System.currentTimeMillis() - startTime;
        
        return SearchResult.builder()
                .query(request.getQuery())
                .results(results)
                .totalResults(results.size())
                .searchTimeMs(searchTime)
                .searchType(searchType)
                .build();
    }
    
    /**
     * Perform semantic search using embeddings
     */
    private List<DocumentChunk> performSemanticSearch(SearchRequest request) {
        List<Double> queryEmbedding = embeddingsService.generateEmbeddings(request.getQuery());
        
        List<DocumentChunk> candidates = getCandidateDocuments(request);
        List<DocumentChunk> results = new ArrayList<>();
        
        for (DocumentChunk chunk : candidates) {
            if (chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty()) {
                double similarity = embeddingsService.calculateSimilarity(queryEmbedding, chunk.getEmbedding());
                
                if (similarity >= request.getMinRelevanceScore()) {
                    chunk.setRelevanceScore(similarity);
                    results.add(chunk);
                }
            }
        }
        
        log.debug("Semantic search found {} results for query '{}'", results.size(), request.getQuery());
        return results;
    }
    
    /**
     * Perform keyword-based search
     */
    private List<DocumentChunk> performKeywordSearch(SearchRequest request) {
        String query = request.getQuery().toLowerCase();
        List<String> queryTerms = Arrays.asList(query.split("\\s+"));
        
        List<DocumentChunk> candidates = getCandidateDocuments(request);
        List<DocumentChunk> results = new ArrayList<>();
        
        for (DocumentChunk chunk : candidates) {
            double score = calculateKeywordScore(chunk, queryTerms, request.isFuzzySearch());
            
            if (score >= request.getMinRelevanceScore()) {
                chunk.setRelevanceScore(score);
                results.add(chunk);
            }
        }
        
        log.debug("Keyword search found {} results for query '{}'", results.size(), request.getQuery());
        return results;
    }
    
    /**
     * Get candidate documents based on filters
     */
    private List<DocumentChunk> getCandidateDocuments(SearchRequest request) {
        Collection<DocumentChunk> allDocs = documentationService.getAllDocuments();
        
        return allDocs.stream()
                .filter(chunk -> matchesFilters(chunk, request))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a document chunk matches the search filters
     */
    private boolean matchesFilters(DocumentChunk chunk, SearchRequest request) {
        // Source filter
        if (request.getSource() != null && !request.getSource().equals(chunk.getSource())) {
            return false;
        }
        
        // Version filter
        if (request.getVersion() != null && !request.getVersion().equals(chunk.getVersion())) {
            return false;
        }
        
        // Category filter
        if (request.getCategory() != null && !request.getCategory().equals(chunk.getCategory())) {
            return false;
        }
        
        // Tags filter
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            if (chunk.getTags() == null || chunk.getTags().isEmpty()) {
                return false;
            }
            
            // Check if any requested tag matches
            boolean hasMatchingTag = request.getTags().stream()
                    .anyMatch(requestedTag -> chunk.getTags().contains(requestedTag));
            
            if (!hasMatchingTag) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Calculate keyword-based relevance score
     */
    private double calculateKeywordScore(DocumentChunk chunk, List<String> queryTerms, boolean fuzzySearch) {
        String content = (chunk.getTitle() + " " + chunk.getContent()).toLowerCase();
        double score = 0.0;
        
        for (String term : queryTerms) {
            if (term.trim().isEmpty()) continue;
            
            double termScore = 0.0;
            
            // Exact matches in title (higher weight)
            if (chunk.getTitle() != null && chunk.getTitle().toLowerCase().contains(term)) {
                termScore += 3.0;
            }
            
            // Exact matches in content
            long exactMatches = countOccurrences(content, term);
            termScore += exactMatches * 1.0;
            
            // Fuzzy matches (if enabled)
            if (fuzzySearch) {
                termScore += calculateFuzzyMatches(content, term) * 0.5;
            }
            
            // Bonus for matches in tags
            if (chunk.getTags() != null) {
                long tagMatches = chunk.getTags().stream()
                        .mapToLong(tag -> countOccurrences(tag.toLowerCase(), term))
                        .sum();
                termScore += tagMatches * 2.0;
            }
            
            // Normalize by content length
            termScore = termScore / Math.log(content.length() + 1);
            
            score += termScore;
        }
        
        // Normalize by number of query terms
        return score / queryTerms.size();
    }
    
    /**
     * Count exact occurrences of a term in text
     */
    private long countOccurrences(String text, String term) {
        if (text == null || term == null) return 0;
        
        int count = 0;
        int index = 0;
        
        while ((index = text.indexOf(term, index)) != -1) {
            count++;
            index += term.length();
        }
        
        return count;
    }
    
    /**
     * Calculate fuzzy matches using simple character-based similarity
     */
    private double calculateFuzzyMatches(String content, String term) {
        String[] words = content.split("\\s+");
        double maxSimilarity = 0.0;
        
        for (String word : words) {
            if (word.length() >= term.length() - 2 && word.length() <= term.length() + 2) {
                double similarity = calculateStringSimilarity(word, term);
                maxSimilarity = Math.max(maxSimilarity, similarity);
            }
        }
        
        return maxSimilarity;
    }
    
    /**
     * Calculate string similarity using Levenshtein distance
     */
    private double calculateStringSimilarity(String s1, String s2) {
        int distance = calculateLevenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        
        if (maxLength == 0) return 1.0;
        
        return 1.0 - (double) distance / maxLength;
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private int calculateLevenshteinDistance(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j],
                            Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }
        
        return dp[m][n];
    }
    
    /**
     * Remove duplicates and apply final filtering
     */
    private List<DocumentChunk> deduplicateAndFilter(List<DocumentChunk> results, SearchRequest request) {
        Map<String, DocumentChunk> uniqueResults = new LinkedHashMap<>();
        
        for (DocumentChunk chunk : results) {
            String key = chunk.getId();
            
            // Keep the result with the higher relevance score
            if (!uniqueResults.containsKey(key) || 
                chunk.getRelevanceScore() > uniqueResults.get(key).getRelevanceScore()) {
                uniqueResults.put(key, chunk);
            }
        }
        
        return uniqueResults.values().stream()
                .filter(chunk -> chunk.getRelevanceScore() >= request.getMinRelevanceScore())
                .collect(Collectors.toList());
    }
    
    /**
     * Search for similar documents to a given document
     */
    public List<DocumentChunk> findSimilarDocuments(String documentId, int maxResults) {
        Optional<DocumentChunk> targetDoc = documentationService.getDocumentById(documentId);
        
        if (targetDoc.isEmpty() || targetDoc.get().getEmbedding() == null) {
            return List.of();
        }
        
        DocumentChunk target = targetDoc.get();
        List<DocumentChunk> candidates = documentationService.getAllDocuments().stream()
                .filter(chunk -> !chunk.getId().equals(documentId))
                .filter(chunk -> chunk.getEmbedding() != null)
                .collect(Collectors.toList());
        
        List<DocumentChunk> similar = new ArrayList<>();
        
        for (DocumentChunk candidate : candidates) {
            double similarity = embeddingsService.calculateSimilarity(
                    target.getEmbedding(), candidate.getEmbedding());
            
            if (similarity > 0.7) { // High similarity threshold
                candidate.setRelevanceScore(similarity);
                similar.add(candidate);
            }
        }
        
        similar.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        
        return similar.stream()
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * Get search suggestions based on partial query
     */
    public List<String> getSearchSuggestions(String partialQuery, int maxSuggestions) {
        if (partialQuery == null || partialQuery.trim().length() < 2) {
            return List.of();
        }
        
        String queryLower = partialQuery.toLowerCase();
        Set<String> suggestions = new HashSet<>();
        
        // Extract common terms from documents
        for (DocumentChunk chunk : documentationService.getAllDocuments()) {
            // From title
            if (chunk.getTitle() != null) {
                String[] titleWords = chunk.getTitle().toLowerCase().split("\\s+");
                for (String word : titleWords) {
                    if (word.startsWith(queryLower) && word.length() > queryLower.length()) {
                        suggestions.add(chunk.getTitle());
                    }
                }
            }
            
            // From tags
            if (chunk.getTags() != null) {
                for (String tag : chunk.getTags()) {
                    if (tag.toLowerCase().contains(queryLower)) {
                        suggestions.add(tag.replace("-", " "));
                    }
                }
            }
        }
        
        return suggestions.stream()
                .limit(maxSuggestions)
                .collect(Collectors.toList());
    }
    
    /**
     * Get search statistics
     */
    public Map<String, Object> getSearchStats() {
        Collection<DocumentChunk> allDocs = documentationService.getAllDocuments();
        
        Map<String, Long> sourceStats = allDocs.stream()
                .collect(Collectors.groupingBy(
                        DocumentChunk::getSource,
                        Collectors.counting()
                ));
        
        Map<String, Long> categoryStats = allDocs.stream()
                .filter(chunk -> chunk.getCategory() != null)
                .collect(Collectors.groupingBy(
                        DocumentChunk::getCategory,
                        Collectors.counting()
                ));
        
        long docsWithEmbeddings = allDocs.stream()
                .mapToLong(chunk -> chunk.getEmbedding() != null ? 1 : 0)
                .sum();
        
        return Map.of(
                "totalDocuments", allDocs.size(),
                "documentsWithEmbeddings", docsWithEmbeddings,
                "documentsBySource", sourceStats,
                "documentsByCategory", categoryStats
        );
    }
}
