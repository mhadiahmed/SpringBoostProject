package com.springboost.docs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a chunk of documentation content with embeddings for semantic search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    
    private String id;
    private String title;
    private String content;
    private String url;
    private String source; // e.g., "spring-boot", "spring-security"
    private String version; // e.g., "3.2.0", "6.1.0"
    private String category; // e.g., "core", "reference", "guide"
    private List<String> tags;
    private Map<String, Object> metadata;
    
    // Embedding data
    private List<Double> embedding;
    private int embeddingDimension;
    
    // Content analysis
    private int wordCount;
    private List<String> codeSnippets;
    private List<String> configurationExamples;
    
    // Indexing metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String checksum; // To detect content changes
    
    // Search relevance
    private double relevanceScore;
    
    /**
     * Create a new document chunk with basic information
     */
    public static DocumentChunk create(String title, String content, String url, String source) {
        return DocumentChunk.builder()
                .title(title)
                .content(content)
                .url(url)
                .source(source)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .wordCount(content.split("\\s+").length)
                .build();
    }
    
    /**
     * Calculate similarity with another document chunk
     */
    public double calculateSimilarity(DocumentChunk other) {
        if (this.embedding == null || other.embedding == null) {
            return 0.0;
        }
        
        if (this.embedding.size() != other.embedding.size()) {
            return 0.0;
        }
        
        // Cosine similarity calculation
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < this.embedding.size(); i++) {
            dotProduct += this.embedding.get(i) * other.embedding.get(i);
            normA += Math.pow(this.embedding.get(i), 2);
            normB += Math.pow(other.embedding.get(i), 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Check if this chunk is similar to another (above threshold)
     */
    public boolean isSimilarTo(DocumentChunk other, double threshold) {
        return calculateSimilarity(other) >= threshold;
    }
    
    /**
     * Get a truncated version of content for display
     */
    public String getContentPreview(int maxLength) {
        if (content == null) {
            return "";
        }
        
        if (content.length() <= maxLength) {
            return content;
        }
        
        return content.substring(0, maxLength) + "...";
    }
}
