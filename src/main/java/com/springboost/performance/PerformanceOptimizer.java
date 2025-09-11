package com.springboost.performance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Performance optimization and monitoring component
 * Provides caching, metrics collection, and performance tuning
 */
@Slf4j
@Component
public class PerformanceOptimizer {
    
    private final Map<String, LongAdder> executionCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalExecutionTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> minExecutionTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> maxExecutionTime = new ConcurrentHashMap<>();
    
    // Performance thresholds
    private static final long SLOW_EXECUTION_THRESHOLD_MS = 1000;
    private static final long VERY_SLOW_EXECUTION_THRESHOLD_MS = 5000;
    
    /**
     * Record operation execution time and update metrics
     */
    public void recordExecution(String operation, long executionTimeMs) {
        executionCounts.computeIfAbsent(operation, k -> new LongAdder()).increment();
        totalExecutionTime.computeIfAbsent(operation, k -> new AtomicLong(0)).addAndGet(executionTimeMs);
        
        // Update min/max times
        minExecutionTime.compute(operation, (k, v) -> 
            v == null ? new AtomicLong(executionTimeMs) : 
            new AtomicLong(Math.min(v.get(), executionTimeMs)));
        
        maxExecutionTime.compute(operation, (k, v) -> 
            v == null ? new AtomicLong(executionTimeMs) : 
            new AtomicLong(Math.max(v.get(), executionTimeMs)));
        
        // Log slow executions
        if (executionTimeMs > VERY_SLOW_EXECUTION_THRESHOLD_MS) {
            log.warn("Very slow execution detected for '{}': {}ms", operation, executionTimeMs);
        } else if (executionTimeMs > SLOW_EXECUTION_THRESHOLD_MS) {
            log.debug("Slow execution detected for '{}': {}ms", operation, executionTimeMs);
        }
    }
    
    /**
     * Execute operation with performance monitoring
     */
    public <T> T executeWithMonitoring(String operation, PerformanceExecutor<T> executor) throws Exception {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = executor.execute();
            long executionTime = System.currentTimeMillis() - startTime;
            recordExecution(operation, executionTime);
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            recordExecution(operation + "_error", executionTime);
            throw e;
        }
    }
    
    /**
     * Get performance statistics for an operation
     */
    public Map<String, Object> getOperationStats(String operation) {
        LongAdder count = executionCounts.get(operation);
        AtomicLong totalTime = totalExecutionTime.get(operation);
        AtomicLong minTime = minExecutionTime.get(operation);
        AtomicLong maxTime = maxExecutionTime.get(operation);
        
        if (count == null || count.sum() == 0) {
            return Map.of("operation", operation, "executed", false);
        }
        
        long executions = count.sum();
        long total = totalTime.get();
        double avgTime = total / (double) executions;
        
        return Map.of(
                "operation", operation,
                "executed", true,
                "executionCount", executions,
                "totalTimeMs", total,
                "averageTimeMs", avgTime,
                "minTimeMs", minTime.get(),
                "maxTimeMs", maxTime.get(),
                "performance", classifyPerformance(avgTime)
        );
    }
    
    /**
     * Get comprehensive performance report
     */
    public Map<String, Object> getPerformanceReport() {
        Map<String, Object> report = new ConcurrentHashMap<>();
        
        // Overall statistics
        long totalOperations = executionCounts.values().stream()
                .mapToLong(LongAdder::sum)
                .sum();
        
        long totalExecutionTimeMs = totalExecutionTime.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
        
        double avgExecutionTime = totalOperations > 0 ? 
                totalExecutionTimeMs / (double) totalOperations : 0;
        
        report.put("summary", Map.of(
                "totalOperations", totalOperations,
                "totalExecutionTimeMs", totalExecutionTimeMs,
                "averageExecutionTimeMs", avgExecutionTime,
                "monitoredOperations", executionCounts.size()
        ));
        
        // Per-operation statistics
        Map<String, Object> operationStats = new ConcurrentHashMap<>();
        for (String operation : executionCounts.keySet()) {
            operationStats.put(operation, getOperationStats(operation));
        }
        report.put("operationStats", operationStats);
        
        // Performance recommendations
        report.put("recommendations", generatePerformanceRecommendations());
        
        // System performance
        report.put("systemPerformance", getSystemPerformanceMetrics());
        
        return report;
    }
    
    /**
     * Generate performance recommendations
     */
    private Map<String, Object> generatePerformanceRecommendations() {
        Map<String, Object> recommendations = new ConcurrentHashMap<>();
        
        // Find slow operations
        var slowOperations = executionCounts.entrySet().stream()
                .filter(entry -> {
                    String operation = entry.getKey();
                    LongAdder count = entry.getValue();
                    if (count.sum() == 0) return false;
                    
                    AtomicLong totalTime = totalExecutionTime.get(operation);
                    double avgTime = totalTime.get() / (double) count.sum();
                    return avgTime > SLOW_EXECUTION_THRESHOLD_MS;
                })
                .map(Map.Entry::getKey)
                .toList();
        
        if (!slowOperations.isEmpty()) {
            recommendations.put("slowOperations", slowOperations);
            recommendations.put("slowOperationsAdvice", 
                "Consider optimizing these operations or implementing caching");
        }
        
        // Memory recommendations
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (usedMemory * 100.0) / maxMemory;
        
        if (memoryUsagePercent > 80) {
            recommendations.put("memoryWarning", true);
            recommendations.put("memoryAdvice", 
                "High memory usage detected (" + String.format("%.1f", memoryUsagePercent) + 
                "%). Consider increasing heap size or optimizing memory usage.");
        }
        
        // Execution frequency recommendations
        var frequentOperations = executionCounts.entrySet().stream()
                .filter(entry -> entry.getValue().sum() > 1000)
                .map(Map.Entry::getKey)
                .toList();
        
        if (!frequentOperations.isEmpty()) {
            recommendations.put("frequentOperations", frequentOperations);
            recommendations.put("frequentOperationsAdvice", 
                "These operations are executed frequently. Consider aggressive caching or optimization.");
        }
        
        return recommendations;
    }
    
    /**
     * Get system performance metrics
     */
    private Map<String, Object> getSystemPerformanceMetrics() {
        Runtime runtime = Runtime.getRuntime();
        
        return Map.of(
                "availableProcessors", runtime.availableProcessors(),
                "totalMemoryMB", runtime.totalMemory() / (1024 * 1024),
                "freeMemoryMB", runtime.freeMemory() / (1024 * 1024),
                "usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                "maxMemoryMB", runtime.maxMemory() / (1024 * 1024),
                "memoryUsagePercent", ((runtime.totalMemory() - runtime.freeMemory()) * 100.0) / runtime.maxMemory(),
                "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Reset performance statistics
     */
    @CacheEvict(cacheNames = {"performance-stats", "operation-stats"}, allEntries = true)
    public void resetStatistics() {
        executionCounts.clear();
        totalExecutionTime.clear();
        minExecutionTime.clear();
        maxExecutionTime.clear();
        log.info("Performance statistics reset");
    }
    
    /**
     * Reset statistics for a specific operation
     */
    public void resetOperationStatistics(String operation) {
        executionCounts.remove(operation);
        totalExecutionTime.remove(operation);
        minExecutionTime.remove(operation);
        maxExecutionTime.remove(operation);
        log.info("Performance statistics reset for operation: {}", operation);
    }
    
    /**
     * Get top performing operations
     */
    @Cacheable(cacheNames = "performance-stats", key = "'top-performers'")
    public Map<String, Object> getTopPerformingOperations(int limit) {
        var topPerformers = executionCounts.entrySet().stream()
                .filter(entry -> entry.getValue().sum() > 0)
                .sorted((e1, e2) -> {
                    // Sort by average execution time (ascending)
                    double avg1 = totalExecutionTime.get(e1.getKey()).get() / (double) e1.getValue().sum();
                    double avg2 = totalExecutionTime.get(e2.getKey()).get() / (double) e2.getValue().sum();
                    return Double.compare(avg1, avg2);
                })
                .limit(limit)
                .map(entry -> {
                    String operation = entry.getKey();
                    long count = entry.getValue().sum();
                    double avgTime = totalExecutionTime.get(operation).get() / (double) count;
                    
                    return Map.of(
                            "operation", operation,
                            "averageTimeMs", avgTime,
                            "executionCount", count
                    );
                })
                .toList();
        
        return Map.of(
                "topPerformers", topPerformers,
                "limit", limit,
                "timestamp", System.currentTimeMillis()
        );
    }
    
    /**
     * Get worst performing operations
     */
    @Cacheable(cacheNames = "performance-stats", key = "'worst-performers'")
    public Map<String, Object> getWorstPerformingOperations(int limit) {
        var worstPerformers = executionCounts.entrySet().stream()
                .filter(entry -> entry.getValue().sum() > 0)
                .sorted((e1, e2) -> {
                    // Sort by average execution time (descending)
                    double avg1 = totalExecutionTime.get(e1.getKey()).get() / (double) e1.getValue().sum();
                    double avg2 = totalExecutionTime.get(e2.getKey()).get() / (double) e2.getValue().sum();
                    return Double.compare(avg2, avg1);
                })
                .limit(limit)
                .map(entry -> {
                    String operation = entry.getKey();
                    long count = entry.getValue().sum();
                    double avgTime = totalExecutionTime.get(operation).get() / (double) count;
                    
                    return Map.of(
                            "operation", operation,
                            "averageTimeMs", avgTime,
                            "executionCount", count,
                            "performance", classifyPerformance(avgTime)
                    );
                })
                .toList();
        
        return Map.of(
                "worstPerformers", worstPerformers,
                "limit", limit,
                "timestamp", System.currentTimeMillis()
        );
    }
    
    private String classifyPerformance(double avgTimeMs) {
        if (avgTimeMs < 50) return "EXCELLENT";
        if (avgTimeMs < 200) return "GOOD";
        if (avgTimeMs < 1000) return "ACCEPTABLE";
        if (avgTimeMs < 5000) return "SLOW";
        return "VERY_SLOW";
    }
    
    @FunctionalInterface
    public interface PerformanceExecutor<T> {
        T execute() throws Exception;
    }
}
