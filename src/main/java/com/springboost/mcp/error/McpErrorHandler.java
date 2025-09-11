package com.springboost.mcp.error;

import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized error handler for MCP operations
 * Provides error recovery, rate limiting, and monitoring
 */
@Slf4j
@Component
public class McpErrorHandler {
    
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastErrorTimestamp = new ConcurrentHashMap<>();
    
    // Error recovery settings
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long ERROR_RESET_INTERVAL_MS = 300_000; // 5 minutes
    private static final int MAX_ERRORS_PER_TOOL = 100;
    
    /**
     * Handle tool execution with automatic retry and error recovery
     */
    public <T> T executeWithErrorHandling(String toolName, ToolExecutor<T> executor) throws McpToolException {
        String errorKey = "tool:" + toolName;
        
        // Check if tool is temporarily disabled due to errors
        if (isToolTemporarilyDisabled(errorKey)) {
            throw new McpToolException(toolName, 
                "Tool temporarily disabled due to repeated errors. Please try again later.");
        }
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                T result = executor.execute();
                
                // Reset error count on successful execution
                if (attempt > 1) {
                    resetErrorCount(errorKey);
                    log.info("Tool '{}' recovered after {} attempts", toolName, attempt);
                }
                
                return result;
                
            } catch (McpToolException e) {
                lastException = e;
                log.warn("Tool '{}' execution failed (attempt {}/{}): {}", 
                    toolName, attempt, MAX_RETRIES, e.getMessage());
                
                // Don't retry for validation errors
                if (isValidationError(e)) {
                    incrementErrorCount(errorKey);
                    throw e;
                }
                
                if (attempt < MAX_RETRIES) {
                    waitBeforeRetry(attempt);
                }
                
            } catch (Exception e) {
                lastException = e;
                log.error("Tool '{}' execution failed with unexpected error (attempt {}/{}): {}", 
                    toolName, attempt, MAX_RETRIES, e.getMessage(), e);
                
                if (attempt < MAX_RETRIES) {
                    waitBeforeRetry(attempt);
                }
            }
        }
        
        // All attempts failed
        incrementErrorCount(errorKey);
        
        if (lastException instanceof McpToolException) {
            throw (McpToolException) lastException;
        } else {
            throw new McpToolException(toolName, 
                "Tool execution failed after " + MAX_RETRIES + " attempts: " + lastException.getMessage(), 
                lastException);
        }
    }
    
    /**
     * Handle search service errors with specific recovery logic
     */
    public <T> T executeSearchWithErrorHandling(String operation, SearchExecutor<T> executor) throws Exception {
        String errorKey = "search:" + operation;
        
        try {
            return executor.execute();
            
        } catch (Exception e) {
            incrementErrorCount(errorKey);
            log.error("Search operation '{}' failed: {}", operation, e.getMessage(), e);
            
            // For search operations, provide fallback results rather than failing completely
            if (executor instanceof FallbackSearchExecutor) {
                try {
                    T fallbackResult = ((FallbackSearchExecutor<T>) executor).executeFallback();
                    log.info("Search operation '{}' using fallback strategy", operation);
                    return fallbackResult;
                } catch (Exception fallbackException) {
                    log.error("Fallback search also failed for '{}': {}", operation, fallbackException.getMessage());
                }
            }
            
            throw e;
        }
    }
    
    /**
     * Handle documentation service errors
     */
    public <T> T executeDocumentationWithErrorHandling(String operation, DocumentationExecutor<T> executor) throws Exception {
        String errorKey = "docs:" + operation;
        
        if (isServiceTemporarilyDisabled(errorKey)) {
            throw new RuntimeException("Documentation service temporarily disabled for operation: " + operation);
        }
        
        try {
            return executor.execute();
            
        } catch (Exception e) {
            incrementErrorCount(errorKey);
            log.error("Documentation operation '{}' failed: {}", operation, e.getMessage());
            
            // For documentation operations, provide graceful degradation
            if (isNetworkRelatedError(e)) {
                log.warn("Network-related error in documentation operation '{}', providing cached results", operation);
                // Would integrate with cache here
            }
            
            throw e;
        }
    }
    
    /**
     * Get error statistics for monitoring
     */
    public Map<String, Object> getErrorStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<String, AtomicLong> entry : errorCounts.entrySet()) {
            String key = entry.getKey();
            AtomicLong count = entry.getValue();
            Long lastError = lastErrorTimestamp.get(key);
            
            Map<String, Object> errorInfo = Map.of(
                    "errorCount", count.get(),
                    "lastErrorTime", lastError != null ? lastError : 0,
                    "timeSinceLastError", lastError != null ? currentTime - lastError : -1,
                    "isDisabled", isComponentTemporarilyDisabled(key, currentTime)
            );
            
            stats.put(key, errorInfo);
        }
        
        return stats;
    }
    
    /**
     * Reset error counts for a specific component
     */
    public void resetErrorCount(String errorKey) {
        errorCounts.remove(errorKey);
        lastErrorTimestamp.remove(errorKey);
        log.info("Reset error count for: {}", errorKey);
    }
    
    /**
     * Reset all error counts
     */
    public void resetAllErrorCounts() {
        errorCounts.clear();
        lastErrorTimestamp.clear();
        log.info("Reset all error counts");
    }
    
    /**
     * Check if a component is healthy
     */
    public boolean isComponentHealthy(String component) {
        String errorKey = getErrorKey(component);
        return !isComponentTemporarilyDisabled(errorKey, System.currentTimeMillis());
    }
    
    /**
     * Get component health status
     */
    public Map<String, Object> getComponentHealthStatus() {
        Map<String, Object> healthStatus = new ConcurrentHashMap<>();
        
        String[] components = {"tools", "search", "documentation", "embeddings"};
        
        for (String component : components) {
            boolean healthy = isComponentHealthy(component);
            AtomicLong errorCount = errorCounts.get(getErrorKey(component));
            
            Map<String, Object> status = Map.of(
                    "healthy", healthy,
                    "errorCount", errorCount != null ? errorCount.get() : 0,
                    "status", healthy ? "UP" : "DOWN"
            );
            
            healthStatus.put(component, status);
        }
        
        return healthStatus;
    }
    
    // Helper methods
    
    private void incrementErrorCount(String errorKey) {
        errorCounts.computeIfAbsent(errorKey, k -> new AtomicLong(0)).incrementAndGet();
        lastErrorTimestamp.put(errorKey, System.currentTimeMillis());
    }
    
    private boolean isToolTemporarilyDisabled(String errorKey) {
        return isComponentTemporarilyDisabled(errorKey, System.currentTimeMillis());
    }
    
    private boolean isServiceTemporarilyDisabled(String errorKey) {
        return isComponentTemporarilyDisabled(errorKey, System.currentTimeMillis());
    }
    
    private boolean isComponentTemporarilyDisabled(String errorKey, long currentTime) {
        AtomicLong errorCount = errorCounts.get(errorKey);
        if (errorCount == null || errorCount.get() < MAX_ERRORS_PER_TOOL) {
            return false;
        }
        
        Long lastError = lastErrorTimestamp.get(errorKey);
        if (lastError == null) {
            return false;
        }
        
        // Reset if enough time has passed
        if (currentTime - lastError > ERROR_RESET_INTERVAL_MS) {
            resetErrorCount(errorKey);
            return false;
        }
        
        return true;
    }
    
    private boolean isValidationError(McpToolException e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("parameter") || 
               message.contains("required") || 
               message.contains("invalid") ||
               message.contains("validation");
    }
    
    private boolean isNetworkRelatedError(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("connection") ||
               message.contains("timeout") ||
               message.contains("network") ||
               message.contains("socket");
    }
    
    private void waitBeforeRetry(int attempt) {
        try {
            // Exponential backoff
            long delay = RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1);
            Thread.sleep(Math.min(delay, 10000)); // Max 10 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Retry wait interrupted");
        }
    }
    
    private String getErrorKey(String component) {
        return component + ":general";
    }
    
    // Functional interfaces for executors
    
    @FunctionalInterface
    public interface ToolExecutor<T> {
        T execute() throws Exception;
    }
    
    @FunctionalInterface
    public interface SearchExecutor<T> {
        T execute() throws Exception;
    }
    
    public interface FallbackSearchExecutor<T> extends SearchExecutor<T> {
        T executeFallback() throws Exception;
    }
    
    @FunctionalInterface
    public interface DocumentationExecutor<T> {
        T execute() throws Exception;
    }
}
