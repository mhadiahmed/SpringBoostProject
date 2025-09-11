package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tool to read the last error from application log files
 * Provides error parsing, stack trace extraction, and categorization
 */
@Slf4j
@Component
public class LastErrorTool implements McpTool {
    
    private final Environment environment;
    
    // Common log patterns
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}).*?(ERROR|FATAL)\\s+(.+?)\\s+-\\s+(.+)",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "^\\s*(\\w+(?:\\.\\w+)*(?:Exception|Error|Throwable))\\s*:?\\s*(.*)",
            Pattern.MULTILINE
    );
    
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "^\\s*at\\s+([\\w.$]+)\\(([^)]+)\\)",
            Pattern.MULTILINE
    );
    
    @Autowired
    public LastErrorTool(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public String getName() {
        return "last-error";
    }
    
    @Override
    public String getDescription() {
        return "Read and analyze the last error from application log files with stack trace parsing";
    }
    
    @Override
    public String getCategory() {
        return "logging";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "logFile", Map.of(
                        "type", "string",
                        "description", "Specific log file to search (if not provided, searches common log locations)"
                ),
                "maxLines", Map.of(
                        "type", "integer",
                        "description", "Maximum number of lines to search from the end of the log",
                        "default", 1000,
                        "minimum", 100,
                        "maximum", 10000
                ),
                "includeStackTrace", Map.of(
                        "type", "boolean",
                        "description", "Include full stack trace in the response",
                        "default", true
                ),
                "parseException", Map.of(
                        "type", "boolean",
                        "description", "Parse and categorize exception details",
                        "default", true
                ),
                "errorLevel", Map.of(
                        "type", "string",
                        "description", "Error level to search for",
                        "enum", Arrays.asList("ERROR", "FATAL", "WARN", "ALL"),
                        "default", "ERROR"
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String logFile = (String) params.get("logFile");
            int maxLines = ((Number) params.getOrDefault("maxLines", 1000)).intValue();
            boolean includeStackTrace = (boolean) params.getOrDefault("includeStackTrace", true);
            boolean parseException = (boolean) params.getOrDefault("parseException", true);
            String errorLevel = (String) params.getOrDefault("errorLevel", "ERROR");
            
            Map<String, Object> result = new HashMap<>();
            
            // Find log file(s) to search
            List<Path> logFiles = findLogFiles(logFile);
            result.put("searchedFiles", logFiles.stream().map(Path::toString).collect(Collectors.toList()));
            
            if (logFiles.isEmpty()) {
                result.put("message", "No log files found to search");
                result.put("found", false);
                return result;
            }
            
            // Search for the last error
            ErrorInfo lastError = findLastError(logFiles, maxLines, errorLevel);
            
            if (lastError == null) {
                result.put("message", "No errors found in the searched log files");
                result.put("found", false);
                result.put("searchedLines", maxLines);
                return result;
            }
            
            result.put("found", true);
            result.put("timestamp", lastError.timestamp);
            result.put("level", lastError.level);
            result.put("logger", lastError.logger);
            result.put("message", lastError.message);
            result.put("logFile", lastError.logFile);
            result.put("lineNumber", lastError.lineNumber);
            
            // Include stack trace if requested
            if (includeStackTrace && lastError.stackTrace != null) {
                result.put("stackTrace", lastError.stackTrace);
                result.put("stackTraceLines", lastError.stackTrace.size());
            }
            
            // Parse exception details if requested
            if (parseException && lastError.exceptionClass != null) {
                Map<String, Object> exceptionInfo = new HashMap<>();
                exceptionInfo.put("class", lastError.exceptionClass);
                exceptionInfo.put("message", lastError.exceptionMessage);
                exceptionInfo.put("category", categorizeException(lastError.exceptionClass));
                exceptionInfo.put("rootCause", findRootCause(lastError.stackTrace));
                result.put("exception", exceptionInfo);
            }
            
            // Analysis
            result.put("analysis", analyzeError(lastError));
            result.put("searchParams", Map.of(
                    "maxLines", maxLines,
                    "errorLevel", errorLevel,
                    "includeStackTrace", includeStackTrace,
                    "parseException", parseException
            ));
            result.put("timestamp", System.currentTimeMillis());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to read last error: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to read last error: " + e.getMessage(), e);
        }
    }
    
    private List<Path> findLogFiles(String specificFile) throws IOException {
        List<Path> logFiles = new ArrayList<>();
        
        if (specificFile != null && !specificFile.isEmpty()) {
            Path path = Paths.get(specificFile);
            if (Files.exists(path) && Files.isReadable(path)) {
                logFiles.add(path);
            }
            return logFiles;
        }
        
        // Common log file locations
        List<String> logLocations = Arrays.asList(
                "logs/spring-boost.log",
                "logs/application.log",
                "spring.log",
                "application.log",
                System.getProperty("java.io.tmpdir") + "/spring.log"
        );
        
        // Add logging.file.name if configured
        String configuredLogFile = environment.getProperty("logging.file.name");
        if (configuredLogFile != null) {
            logLocations.add(0, configuredLogFile);
        }
        
        // Add logging.file.path if configured
        String logPath = environment.getProperty("logging.file.path");
        if (logPath != null) {
            logLocations.add(0, logPath + "/spring.log");
        }
        
        for (String location : logLocations) {
            Path path = Paths.get(location);
            if (Files.exists(path) && Files.isReadable(path)) {
                logFiles.add(path);
            }
        }
        
        return logFiles;
    }
    
    private ErrorInfo findLastError(List<Path> logFiles, int maxLines, String errorLevel) throws IOException {
        ErrorInfo lastError = null;
        LocalDateTime latestTime = null;
        
        for (Path logFile : logFiles) {
            ErrorInfo error = searchLogFile(logFile, maxLines, errorLevel);
            if (error != null) {
                if (latestTime == null || error.parsedTimestamp.isAfter(latestTime)) {
                    lastError = error;
                    latestTime = error.parsedTimestamp;
                }
            }
        }
        
        return lastError;
    }
    
    private ErrorInfo searchLogFile(Path logFile, int maxLines, String errorLevel) throws IOException {
        List<String> lines = readLastLines(logFile, maxLines);
        
        ErrorInfo lastError = null;
        
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            
            // Check if this line matches error pattern
            Matcher errorMatcher = ERROR_PATTERN.matcher(line);
            if (errorMatcher.find()) {
                String timestamp = errorMatcher.group(1);
                String level = errorMatcher.group(2);
                String logger = errorMatcher.group(3);
                String message = errorMatcher.group(4);
                
                // Check if this matches our error level filter
                if (!"ALL".equals(errorLevel) && !level.toUpperCase().equals(errorLevel.toUpperCase())) {
                    continue;
                }
                
                ErrorInfo error = new ErrorInfo();
                error.timestamp = timestamp;
                error.level = level;
                error.logger = logger;
                error.message = message;
                error.logFile = logFile.toString();
                error.lineNumber = lines.size() - i;
                error.parsedTimestamp = parseTimestamp(timestamp);
                
                // Look for stack trace
                List<String> stackTrace = collectStackTrace(lines, i + 1);
                if (!stackTrace.isEmpty()) {
                    error.stackTrace = stackTrace;
                    
                    // Parse exception from first stack trace line
                    if (!stackTrace.isEmpty()) {
                        parseException(stackTrace.get(0), error);
                    }
                }
                
                // Return the first (most recent) error we find
                return error;
            }
        }
        
        return null;
    }
    
    private List<String> readLastLines(Path file, int maxLines) throws IOException {
        List<String> allLines = Files.readAllLines(file);
        int startIndex = Math.max(0, allLines.size() - maxLines);
        return allLines.subList(startIndex, allLines.size());
    }
    
    private List<String> collectStackTrace(List<String> lines, int startIndex) {
        List<String> stackTrace = new ArrayList<>();
        
        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i);
            
            // Stop collecting when we hit another log message or empty line
            if (ERROR_PATTERN.matcher(line).find() || line.trim().isEmpty()) {
                break;
            }
            
            // Include stack trace lines and exception messages
            if (line.matches("^\\s*(at\\s+|Caused by:|\\w+Exception:|\\w+Error:).*")) {
                stackTrace.add(line.trim());
            } else if (!stackTrace.isEmpty() && line.matches("^\\s+.*")) {
                // Continuation of previous line
                stackTrace.add(line.trim());
            } else if (!stackTrace.isEmpty()) {
                // Non-stack trace line after we started collecting - stop
                break;
            }
        }
        
        return stackTrace;
    }
    
    private void parseException(String firstStackLine, ErrorInfo error) {
        Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(firstStackLine);
        if (exceptionMatcher.find()) {
            error.exceptionClass = exceptionMatcher.group(1);
            error.exceptionMessage = exceptionMatcher.group(2);
        }
    }
    
    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            // Try common timestamp formats
            String[] formats = {
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    "yyyy-MM-dd HH:mm:ss,SSS",
                    "yyyy-MM-dd HH:mm:ss"
            };
            
            for (String format : formats) {
                try {
                    return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern(format));
                } catch (Exception e) {
                    // Try next format
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse timestamp: {}", timestamp);
        }
        
        return LocalDateTime.now(); // Fallback
    }
    
    private String categorizeException(String exceptionClass) {
        if (exceptionClass == null) return "Unknown";
        
        String lowerClass = exceptionClass.toLowerCase();
        
        if (lowerClass.contains("nullpointer")) return "NullPointerException";
        if (lowerClass.contains("classnotfound")) return "ClassNotFound";
        if (lowerClass.contains("sql") || lowerClass.contains("database")) return "Database";
        if (lowerClass.contains("io") || lowerClass.contains("file")) return "IO/File";
        if (lowerClass.contains("security") || lowerClass.contains("access")) return "Security";
        if (lowerClass.contains("validation")) return "Validation";
        if (lowerClass.contains("http") || lowerClass.contains("web")) return "HTTP/Web";
        if (lowerClass.contains("json") || lowerClass.contains("parse")) return "Parsing";
        if (lowerClass.contains("timeout")) return "Timeout";
        if (lowerClass.contains("connection")) return "Connection";
        
        return "Application";
    }
    
    private String findRootCause(List<String> stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) return null;
        
        // Look for "Caused by:" lines
        for (int i = stackTrace.size() - 1; i >= 0; i--) {
            String line = stackTrace.get(i);
            if (line.startsWith("Caused by:")) {
                return line.substring("Caused by:".length()).trim();
            }
        }
        
        // If no "Caused by" found, return the first exception
        return stackTrace.get(0);
    }
    
    private Map<String, Object> analyzeError(ErrorInfo error) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Time since error
        long minutesAgo = java.time.Duration.between(error.parsedTimestamp, LocalDateTime.now()).toMinutes();
        analysis.put("minutesAgo", minutesAgo);
        analysis.put("recent", minutesAgo < 60);
        
        // Error frequency indicators
        if (error.message != null) {
            analysis.put("hasMessage", true);
            analysis.put("messageLength", error.message.length());
        }
        
        if (error.stackTrace != null) {
            analysis.put("hasStackTrace", true);
            analysis.put("stackTraceDepth", error.stackTrace.size());
            
            // Analyze stack trace for common patterns
            long appStackFrames = error.stackTrace.stream()
                    .filter(line -> line.contains("at com.") || line.contains("at org."))
                    .count();
            analysis.put("applicationStackFrames", appStackFrames);
            analysis.put("hasApplicationCode", appStackFrames > 0);
        }
        
        // Severity assessment
        String severity = "Medium";
        if ("FATAL".equals(error.level)) severity = "High";
        if (error.exceptionClass != null && 
            (error.exceptionClass.contains("OutOfMemoryError") || 
             error.exceptionClass.contains("StackOverflowError"))) {
            severity = "Critical";
        }
        analysis.put("severity", severity);
        
        return analysis;
    }
    
    private static class ErrorInfo {
        String timestamp;
        String level;
        String logger;
        String message;
        String logFile;
        int lineNumber;
        LocalDateTime parsedTimestamp;
        List<String> stackTrace;
        String exceptionClass;
        String exceptionMessage;
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "basic", Map.of(
                        "description", "Get the last error from default log locations",
                        "parameters", Map.of()
                ),
                "specificFile", Map.of(
                        "description", "Search a specific log file",
                        "parameters", Map.of("logFile", "/var/log/application.log")
                ),
                "withoutStackTrace", Map.of(
                        "description", "Get error summary without full stack trace",
                        "parameters", Map.of("includeStackTrace", false)
                ),
                "allErrorLevels", Map.of(
                        "description", "Search for any error level (ERROR, WARN, FATAL)",
                        "parameters", Map.of("errorLevel", "ALL")
                ),
                "deepSearch", Map.of(
                        "description", "Search more lines for older errors",
                        "parameters", Map.of("maxLines", 5000)
                )
        );
    }
}
