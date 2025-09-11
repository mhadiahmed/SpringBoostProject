package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool to collect and analyze browser console logs and errors
 * Provides JavaScript error tracking, network request failures, and performance metrics
 */
@Slf4j
@Component
public class BrowserLogsTool implements McpTool {
    
    private final Environment environment;
    
    // Simulated browser log storage (in a real implementation, this would come from a WebSocket/SSE endpoint)
    private static final List<BrowserLogEntry> SIMULATED_LOGS = Arrays.asList(
            new BrowserLogEntry("error", "TypeError: Cannot read property 'id' of undefined", 
                    "main.js:45:12", LocalDateTime.now().minusMinutes(5), 
                    "http://localhost:8080/dashboard", "TypeError"),
            new BrowserLogEntry("warn", "Resource loading timeout", 
                    "network", LocalDateTime.now().minusMinutes(3),
                    "http://localhost:8080/api/data", "NetworkWarning"),
            new BrowserLogEntry("error", "Failed to fetch user data: 500 Internal Server Error",
                    "api-client.js:123:8", LocalDateTime.now().minusMinutes(2),
                    "http://localhost:8080/users", "NetworkError"),
            new BrowserLogEntry("info", "User login successful",
                    "auth.js:67:4", LocalDateTime.now().minusMinutes(1),
                    "http://localhost:8080/login", "Info"),
            new BrowserLogEntry("error", "Uncaught ReferenceError: $ is not defined",
                    "legacy.js:12:1", LocalDateTime.now().minusSeconds(30),
                    "http://localhost:8080/admin", "ReferenceError")
    );
    
    @Autowired
    public BrowserLogsTool(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public String getName() {
        return "browser-logs";
    }
    
    @Override
    public String getDescription() {
        return "Collect browser console errors, JavaScript exceptions, network failures, and performance metrics";
    }
    
    @Override
    public String getCategory() {
        return "monitoring";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "logLevel", Map.of(
                        "type", "string",
                        "description", "Filter by log level",
                        "enum", Arrays.asList("all", "error", "warn", "info", "debug"),
                        "default", "all"
                ),
                "errorType", Map.of(
                        "type", "string",
                        "description", "Filter by JavaScript error type",
                        "enum", Arrays.asList("all", "TypeError", "ReferenceError", "SyntaxError", "NetworkError", "SecurityError"),
                        "default", "all"
                ),
                "url", Map.of(
                        "type", "string",
                        "description", "Filter by page URL or URL pattern"
                ),
                "since", Map.of(
                        "type", "string",
                        "description", "Only show entries since this time (ISO format: yyyy-MM-ddTHH:mm:ss)"
                ),
                "includeNetworkErrors", Map.of(
                        "type", "boolean",
                        "description", "Include network request failures",
                        "default", true
                ),
                "includePerformance", Map.of(
                        "type", "boolean",
                        "description", "Include performance metrics",
                        "default", false
                ),
                "includeStackTraces", Map.of(
                        "type", "boolean",
                        "description", "Include JavaScript stack traces",
                        "default", true
                ),
                "groupBy", Map.of(
                        "type", "string",
                        "description", "Group results by specific criteria",
                        "enum", Arrays.asList("none", "url", "errorType", "source"),
                        "default", "none"
                ),
                "maxEntries", Map.of(
                        "type", "integer",
                        "description", "Maximum number of log entries to return",
                        "default", 100,
                        "minimum", 1,
                        "maximum", 1000
                ),
                "format", Map.of(
                        "type", "string",
                        "description", "Output format",
                        "enum", Arrays.asList("detailed", "summary", "errors-only"),
                        "default", "detailed"
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String logLevel = (String) params.getOrDefault("logLevel", "all");
            String errorType = (String) params.getOrDefault("errorType", "all");
            String url = (String) params.get("url");
            String since = (String) params.get("since");
            boolean includeNetworkErrors = (boolean) params.getOrDefault("includeNetworkErrors", true);
            boolean includePerformance = (boolean) params.getOrDefault("includePerformance", false);
            boolean includeStackTraces = (boolean) params.getOrDefault("includeStackTraces", true);
            String groupBy = (String) params.getOrDefault("groupBy", "none");
            int maxEntries = ((Number) params.getOrDefault("maxEntries", 100)).intValue();
            String format = (String) params.getOrDefault("format", "detailed");
            
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", System.currentTimeMillis());
            result.put("collectionMethod", "simulated"); // In real implementation: "websocket" or "polling"
            
            // Parse time filter
            LocalDateTime sinceTime = parseDateTime(since);
            
            // Get and filter browser logs
            List<BrowserLogEntry> logs = getBrowserLogs(logLevel, errorType, url, sinceTime, 
                    includeNetworkErrors, maxEntries);
            
            result.put("totalEntries", logs.size());
            result.put("filters", getAppliedFilters(logLevel, errorType, url, since, includeNetworkErrors));
            
            // Include performance metrics if requested
            if (includePerformance) {
                result.put("performance", getPerformanceMetrics());
            }
            
            // Format and group results
            switch (format) {
                case "summary":
                    result.put("summary", createLogSummary(logs));
                    result.put("recentErrors", logs.stream()
                            .filter(l -> "error".equals(l.getLevel()))
                            .limit(5)
                            .map(this::formatLogEntryBrief)
                            .collect(Collectors.toList()));
                    break;
                    
                case "errors-only":
                    List<BrowserLogEntry> errors = logs.stream()
                            .filter(l -> "error".equals(l.getLevel()))
                            .collect(Collectors.toList());
                    result.put("errors", errors.stream()
                            .map(l -> formatLogEntry(l, includeStackTraces))
                            .collect(Collectors.toList()));
                    result.put("errorCount", errors.size());
                    break;
                    
                case "detailed":
                default:
                    if ("none".equals(groupBy)) {
                        result.put("entries", logs.stream()
                                .map(l -> formatLogEntry(l, includeStackTraces))
                                .collect(Collectors.toList()));
                    } else {
                        result.put("groupedEntries", groupLogEntries(logs, groupBy, includeStackTraces));
                    }
                    break;
            }
            
            // Add browser information
            result.put("browserInfo", getBrowserInfo());
            
            // Add suggestions for common issues
            if (logs.stream().anyMatch(l -> "error".equals(l.getLevel()))) {
                result.put("suggestions", generateErrorSuggestions(logs));
            }
            
            return result;
            
        } catch (Exception e) {
            if (e instanceof McpToolException) {
                throw (McpToolException) e;
            }
            log.error("Failed to collect browser logs: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to collect browser logs: " + e.getMessage(), e);
        }
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            return LocalDateTime.parse(dateTimeStr.replace("T", " "), 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("Could not parse datetime: {}. Expected format: yyyy-MM-ddTHH:mm:ss", dateTimeStr);
            return null;
        }
    }
    
    private List<BrowserLogEntry> getBrowserLogs(String logLevel, String errorType, String url,
                                               LocalDateTime since, boolean includeNetworkErrors, int maxEntries) {
        // In a real implementation, this would collect logs from:
        // - WebSocket connections from browser clients
        // - Server-sent events
        // - REST endpoint for log submission
        // - Browser extension or injected JavaScript
        
        return SIMULATED_LOGS.stream()
                .filter(log -> {
                    // Level filter
                    if (!"all".equals(logLevel) && !logLevel.equals(log.getLevel())) {
                        return false;
                    }
                    
                    // Error type filter
                    if (!"all".equals(errorType) && !errorType.equals(log.getErrorType())) {
                        return false;
                    }
                    
                    // URL filter
                    if (url != null && !url.trim().isEmpty()) {
                        if (!log.getUrl().contains(url)) {
                            return false;
                        }
                    }
                    
                    // Time filter
                    if (since != null && log.getTimestamp().isBefore(since)) {
                        return false;
                    }
                    
                    // Network error filter
                    if (!includeNetworkErrors && "NetworkError".equals(log.getErrorType())) {
                        return false;
                    }
                    
                    return true;
                })
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())) // Most recent first
                .limit(maxEntries)
                .collect(Collectors.toList());
    }
    
    private Map<String, Object> getAppliedFilters(String logLevel, String errorType, String url, 
                                                String since, boolean includeNetworkErrors) {
        Map<String, Object> filters = new HashMap<>();
        if (!"all".equals(logLevel)) {
            filters.put("logLevel", logLevel);
        }
        if (!"all".equals(errorType)) {
            filters.put("errorType", errorType);
        }
        if (url != null && !url.trim().isEmpty()) {
            filters.put("url", url);
        }
        if (since != null) {
            filters.put("since", since);
        }
        filters.put("includeNetworkErrors", includeNetworkErrors);
        return filters;
    }
    
    private Map<String, Object> getPerformanceMetrics() {
        // Simulated performance metrics
        Map<String, Object> performance = new HashMap<>();
        
        performance.put("pageLoadTime", Map.of(
                "average", 1250,
                "p95", 2100,
                "p99", 3500,
                "unit", "milliseconds"
        ));
        
        performance.put("resourceLoading", Map.of(
                "totalResources", 23,
                "failedLoads", 1,
                "slowResources", Arrays.asList(
                        Map.of("url", "/api/large-data", "loadTime", 3200),
                        Map.of("url", "/images/banner.jpg", "loadTime", 1800)
                )
        ));
        
        performance.put("memoryUsage", Map.of(
                "heapUsed", "45.2 MB",
                "heapTotal", "67.8 MB",
                "heapLimit", "512 MB"
        ));
        
        performance.put("networkRequests", Map.of(
                "total", 157,
                "failed", 3,
                "averageResponseTime", 245,
                "slowestRequest", Map.of(
                        "url", "/api/dashboard/data",
                        "responseTime", 2100,
                        "status", 200
                )
        ));
        
        return performance;
    }
    
    private Map<String, Object> createLogSummary(List<BrowserLogEntry> logs) {
        Map<String, Object> summary = new HashMap<>();
        
        // Count by level
        Map<String, Long> levelCounts = logs.stream()
                .collect(Collectors.groupingBy(BrowserLogEntry::getLevel, Collectors.counting()));
        summary.put("levelCounts", levelCounts);
        
        // Count by error type
        Map<String, Long> errorTypeCounts = logs.stream()
                .filter(l -> l.getErrorType() != null)
                .collect(Collectors.groupingBy(BrowserLogEntry::getErrorType, Collectors.counting()));
        summary.put("errorTypeCounts", errorTypeCounts);
        
        // Top problematic URLs
        Map<String, Long> urlCounts = logs.stream()
                .filter(l -> "error".equals(l.getLevel()))
                .collect(Collectors.groupingBy(BrowserLogEntry::getUrl, Collectors.counting()));
        summary.put("problematicUrls", urlCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                )));
        
        // Time range
        if (!logs.isEmpty()) {
            summary.put("timeRange", Map.of(
                    "earliest", logs.stream()
                            .map(BrowserLogEntry::getTimestamp)
                            .min(LocalDateTime::compareTo)
                            .map(LocalDateTime::toString)
                            .orElse("unknown"),
                    "latest", logs.stream()
                            .map(BrowserLogEntry::getTimestamp)
                            .max(LocalDateTime::compareTo)
                            .map(LocalDateTime::toString)
                            .orElse("unknown")
            ));
        }
        
        return summary;
    }
    
    private Map<String, Object> formatLogEntry(BrowserLogEntry entry, boolean includeStackTraces) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("timestamp", entry.getTimestamp().toString());
        formatted.put("level", entry.getLevel());
        formatted.put("message", entry.getMessage());
        formatted.put("source", entry.getSource());
        formatted.put("url", entry.getUrl());
        formatted.put("errorType", entry.getErrorType());
        
        if (includeStackTraces && entry.getStackTrace() != null) {
            formatted.put("stackTrace", entry.getStackTrace());
        }
        
        // Add additional context for network errors
        if ("NetworkError".equals(entry.getErrorType())) {
            formatted.put("networkDetails", Map.of(
                    "status", entry.getNetworkStatus(),
                    "method", entry.getNetworkMethod(),
                    "responseTime", entry.getNetworkResponseTime()
            ));
        }
        
        return formatted;
    }
    
    private Map<String, Object> formatLogEntryBrief(BrowserLogEntry entry) {
        Map<String, Object> brief = new HashMap<>();
        brief.put("timestamp", entry.getTimestamp().toString());
        brief.put("level", entry.getLevel());
        brief.put("message", truncateMessage(entry.getMessage(), 80));
        brief.put("errorType", entry.getErrorType());
        brief.put("source", entry.getSource());
        return brief;
    }
    
    private String truncateMessage(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }
    
    private Map<String, Object> groupLogEntries(List<BrowserLogEntry> logs, String groupBy, boolean includeStackTraces) {
        Map<String, Object> result = new HashMap<>();
        
        switch (groupBy) {
            case "url":
                Map<String, List<Map<String, Object>>> urlGrouped = logs.stream()
                        .collect(Collectors.groupingBy(
                                BrowserLogEntry::getUrl,
                                Collectors.mapping(l -> formatLogEntry(l, includeStackTraces), Collectors.toList())
                        ));
                urlGrouped.forEach((k, v) -> result.put(k, v));
                break;
                        
            case "errorType":
                Map<String, List<Map<String, Object>>> errorTypeGrouped = logs.stream()
                        .filter(l -> l.getErrorType() != null)
                        .collect(Collectors.groupingBy(
                                BrowserLogEntry::getErrorType,
                                Collectors.mapping(l -> formatLogEntry(l, includeStackTraces), Collectors.toList())
                        ));
                errorTypeGrouped.forEach((k, v) -> result.put(k, v));
                break;
                        
            case "source":
                Map<String, List<Map<String, Object>>> sourceGrouped = logs.stream()
                        .collect(Collectors.groupingBy(
                                BrowserLogEntry::getSource,
                                Collectors.mapping(l -> formatLogEntry(l, includeStackTraces), Collectors.toList())
                        ));
                sourceGrouped.forEach((k, v) -> result.put(k, v));
                break;
                        
            default:
                result.put("ungrouped", logs.stream()
                        .map(l -> formatLogEntry(l, includeStackTraces))
                        .collect(Collectors.toList()));
                break;
        }
        
        return result;
    }
    
    private Map<String, Object> getBrowserInfo() {
        // In a real implementation, this would come from the browser
        return Map.of(
                "userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "platform", "Win32",
                "language", "en-US",
                "cookiesEnabled", true,
                "javaEnabled", false,
                "onlineStatus", true,
                "screenResolution", "1920x1080",
                "colorDepth", 24,
                "timeZone", "America/New_York"
        );
    }
    
    private List<String> generateErrorSuggestions(List<BrowserLogEntry> logs) {
        List<String> suggestions = new ArrayList<>();
        
        // Check for common error patterns
        boolean hasTypeErrors = logs.stream().anyMatch(l -> "TypeError".equals(l.getErrorType()));
        boolean hasReferenceErrors = logs.stream().anyMatch(l -> "ReferenceError".equals(l.getErrorType()));
        boolean hasNetworkErrors = logs.stream().anyMatch(l -> "NetworkError".equals(l.getErrorType()));
        
        if (hasTypeErrors) {
            suggestions.add("TypeError detected - Check for undefined variables or null object access");
            suggestions.add("Consider adding null checks before accessing object properties");
        }
        
        if (hasReferenceErrors) {
            suggestions.add("ReferenceError detected - Verify all required scripts are loaded");
            suggestions.add("Check for missing library dependencies (jQuery, etc.)");
        }
        
        if (hasNetworkErrors) {
            suggestions.add("Network errors detected - Check API endpoint availability");
            suggestions.add("Verify CORS configuration for cross-origin requests");
            suggestions.add("Check for proper error handling in AJAX calls");
        }
        
        // Check for specific error patterns
        boolean hasDollarUndefined = logs.stream()
                .anyMatch(l -> l.getMessage().contains("$ is not defined"));
        if (hasDollarUndefined) {
            suggestions.add("jQuery not loaded - Add jQuery script before other scripts");
        }
        
        boolean hasConsoleErrors = logs.stream()
                .anyMatch(l -> "error".equals(l.getLevel()));
        if (hasConsoleErrors) {
            suggestions.add("Review browser developer tools for additional error details");
            suggestions.add("Enable source maps for better debugging information");
        }
        
        return suggestions.stream().distinct().limit(5).collect(Collectors.toList());
    }
    
    // Browser log entry data class
    private static class BrowserLogEntry {
        private String level;
        private String message;
        private String source;
        private LocalDateTime timestamp;
        private String url;
        private String errorType;
        private String stackTrace;
        private Integer networkStatus;
        private String networkMethod;
        private Long networkResponseTime;
        
        public BrowserLogEntry(String level, String message, String source, 
                             LocalDateTime timestamp, String url, String errorType) {
            this.level = level;
            this.message = message;
            this.source = source;
            this.timestamp = timestamp;
            this.url = url;
            this.errorType = errorType;
            
            // Add simulated stack trace for errors
            if ("error".equals(level)) {
                this.stackTrace = generateSimulatedStackTrace(source, message);
            }
            
            // Add simulated network details for network errors
            if ("NetworkError".equals(errorType)) {
                this.networkStatus = 500;
                this.networkMethod = "GET";
                this.networkResponseTime = 5000L;
            }
        }
        
        private String generateSimulatedStackTrace(String source, String message) {
            return String.format("""
                    at %s
                    at Function.loadData (api-client.js:45:8)
                    at HTMLButtonElement.<anonymous> (main.js:123:12)
                    at HTMLButtonElement.dispatch (jquery.min.js:2:43064)
                    at HTMLButtonElement.v.handle (jquery.min.js:2:41048)
                    """, source);
        }
        
        // Getters
        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public String getSource() { return source; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getUrl() { return url; }
        public String getErrorType() { return errorType; }
        public String getStackTrace() { return stackTrace; }
        public Integer getNetworkStatus() { return networkStatus; }
        public String getNetworkMethod() { return networkMethod; }
        public Long getNetworkResponseTime() { return networkResponseTime; }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "allErrors", Map.of(
                        "description", "Get all JavaScript errors",
                        "parameters", Map.of("logLevel", "error")
                ),
                "typeErrors", Map.of(
                        "description", "Get only TypeError exceptions",
                        "parameters", Map.of(
                                "logLevel", "error",
                                "errorType", "TypeError"
                        )
                ),
                "urlFiltered", Map.of(
                        "description", "Get logs for specific page",
                        "parameters", Map.of("url", "/dashboard")
                ),
                "withPerformance", Map.of(
                        "description", "Include performance metrics",
                        "parameters", Map.of(
                                "includePerformance", true,
                                "format", "detailed"
                        )
                ),
                "errorSummary", Map.of(
                        "description", "Get error summary and statistics",
                        "parameters", Map.of("format", "summary")
                ),
                "groupedByType", Map.of(
                        "description", "Group errors by type",
                        "parameters", Map.of(
                                "groupBy", "errorType",
                                "logLevel", "error"
                        )
                )
        );
    }
}
