package com.springboost.docs.service;

import com.springboost.config.SpringBoostProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for generating and managing text embeddings for semantic search
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingsService {
    
    private final SpringBoostProperties properties;
    
    // Simple in-memory cache for embeddings
    private final Map<String, List<Double>> embeddingsCache = new ConcurrentHashMap<>();
    
    /**
     * Generate embeddings for a given text
     */
    public List<Double> generateEmbeddings(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String textHash = generateTextHash(text);
        
        // Check cache first
        if (embeddingsCache.containsKey(textHash)) {
            log.debug("Retrieved embeddings from cache for text hash: {}", textHash);
            return embeddingsCache.get(textHash);
        }
        
        List<Double> embeddings;
        
        // Use configured embeddings provider
        String provider = properties.getDocumentation().getEmbeddingsProvider();
        
        switch (provider.toLowerCase()) {
            case "openai" -> embeddings = generateOpenAIEmbeddings(text);
            case "local" -> embeddings = generateLocalEmbeddings(text);
            case "simple" -> embeddings = generateSimpleEmbeddings(text);
            default -> {
                log.warn("Unknown embeddings provider '{}', falling back to simple embeddings", provider);
                embeddings = generateSimpleEmbeddings(text);
            }
        }
        
        // Cache the result
        embeddingsCache.put(textHash, embeddings);
        
        log.debug("Generated embeddings with dimension {} for text of length {}", 
                embeddings.size(), text.length());
        
        return embeddings;
    }
    
    /**
     * Generate embeddings using OpenAI API
     */
    private List<Double> generateOpenAIEmbeddings(String text) {
        try {
            // For now, we'll use a simple implementation
            // In a real implementation, you would call the OpenAI API
            log.info("OpenAI embeddings not implemented yet, falling back to simple embeddings");
            return generateSimpleEmbeddings(text);
            
        } catch (Exception e) {
            log.error("Failed to generate OpenAI embeddings, falling back to simple embeddings", e);
            return generateSimpleEmbeddings(text);
        }
    }
    
    /**
     * Generate embeddings using a local model
     */
    private List<Double> generateLocalEmbeddings(String text) {
        // Placeholder for local model implementation
        log.info("Local embeddings not implemented yet, falling back to simple embeddings");
        return generateSimpleEmbeddings(text);
    }
    
    /**
     * Generate simple embeddings using basic text features
     * This is a fallback implementation for demonstration purposes
     */
    private List<Double> generateSimpleEmbeddings(String text) {
        String normalizedText = text.toLowerCase().trim();
        List<Double> embeddings = new ArrayList<>();
        
        // Feature 1: Text length (normalized)
        embeddings.add((double) normalizedText.length() / 1000.0);
        
        // Feature 2-10: Keyword presence (Spring-related terms)
        String[] keywords = {
            "spring", "boot", "security", "data", "web", 
            "controller", "service", "repository", "configuration", "bean"
        };
        
        for (String keyword : keywords) {
            double frequency = countOccurrences(normalizedText, keyword) / 100.0;
            embeddings.add(Math.min(frequency, 1.0)); // Cap at 1.0
        }
        
        // Feature 11-15: Code patterns
        String[] codePatterns = {
            "@", "public", "class", "import", "new"
        };
        
        for (String pattern : codePatterns) {
            double frequency = countOccurrences(normalizedText, pattern) / 50.0;
            embeddings.add(Math.min(frequency, 1.0));
        }
        
        // Feature 16-20: Structural elements
        embeddings.add(countOccurrences(normalizedText, "{") / 20.0); // Braces
        embeddings.add(countOccurrences(normalizedText, "(") / 30.0); // Parentheses
        embeddings.add(countOccurrences(normalizedText, ".") / 100.0); // Dots
        embeddings.add(countOccurrences(normalizedText, ";") / 50.0); // Semicolons
        embeddings.add(countOccurrences(normalizedText, "\n") / 100.0); // Line breaks
        
        // Ensure we have a fixed dimension (50 dimensions for simplicity)
        while (embeddings.size() < 50) {
            embeddings.add(0.0);
        }
        
        // Normalize the vector
        return normalizeVector(embeddings);
    }
    
    /**
     * Count occurrences of a substring in text
     */
    private double countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        
        return count;
    }
    
    /**
     * Normalize a vector to unit length
     */
    private List<Double> normalizeVector(List<Double> vector) {
        double magnitudeSquared = vector.stream()
                .mapToDouble(Double::doubleValue)
                .map(x -> x * x)
                .sum();
        
        double magnitude = Math.sqrt(magnitudeSquared);
        
        if (magnitude == 0.0) {
            return vector;
        }
        
        final double finalMagnitude = magnitude;
        return vector.stream()
                .map(x -> x / finalMagnitude)
                .toList();
    }
    
    /**
     * Generate a hash for text to use as cache key
     */
    private String generateTextHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate text hash", e);
            return String.valueOf(text.hashCode());
        }
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors
     */
    public double calculateSimilarity(List<Double> vector1, List<Double> vector2) {
        if (vector1 == null || vector2 == null || vector1.size() != vector2.size()) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            normA += Math.pow(vector1.get(i), 2);
            normB += Math.pow(vector2.get(i), 2);
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Clear the embeddings cache
     */
    public void clearCache() {
        embeddingsCache.clear();
        log.info("Embeddings cache cleared");
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
                "cacheSize", embeddingsCache.size(),
                "cacheKeys", embeddingsCache.keySet().size()
        );
    }
}
