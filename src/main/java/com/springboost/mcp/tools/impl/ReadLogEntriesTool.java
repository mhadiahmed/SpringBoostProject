package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tool to read and filter application log entries
 * Provides real-time log streaming and pattern-based searching capabilities
 */
@Slf4j
@Component
public class ReadLogEntriesTool implements McpTool {
    
    private final Environment environment;
    
    // Common log patterns
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[\\.,]\\d{3})\\s+\\[(\\w+)\\]\\s+(\\w+)\\s+(.+?)\\s+-\\s+(.+)"
    );
    
    private static final Pattern SPRING_BOOT_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+\\[(.+?)\\]\\s+(\\w+)\\s+(.+?)\\s+:\\s+(.+)"
    );
    
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}).*?\\[(\\w+)\\]\\s+(.+)"
    );
    
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };
    
    @Autowired
    public ReadLogEntriesTool(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public String getName() {
        return "read-log-entries";
    }
    
    @Override
    public String getDescription() {
        return "Read last N log entries with filtering by level, pattern searching, and real-time capabilities";
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
                "lines", Map.of(
                        "type", "integer",
                        "description", "Number of log lines to read from the end",
                        "default", 100,
                        "minimum", 1,
                        "maximum", 10000
                ),
                "level", Map.of(
                        "type", "string",
                        "description", "Filter by log level",
                        "enum", Arrays.asList("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"),
                        "default", "ALL"
                ),
                "pattern", Map.of(
                        "type", "string",
                        "description", "Search pattern to filter log entries (regex supported)"
                ),
                "logFile", Map.of(
                        "type", "string",
                        "description", "Specific log file path (if not provided, auto-detects)"
                ),
                "since", Map.of(
                        "type", "string",
                        "description", "Only show entries since this time (ISO format: yyyy-MM-ddTHH:mm:ss)"
                ),
                "until", Map.of(
                        "type", "string",
                        "description", "Only show entries until this time (ISO format: yyyy-MM-ddTHH:mm:ss)"
                ),
                "includeStackTrace", Map.of(
                        "type", "boolean",
                        "description", "Include full stack traces in output",
                        "default", true
                ),
                "groupByThread", Map.of(
                        "type", "boolean",
                        "description", "Group log entries by thread",
                        "default", false
                ),
                "format", Map.of(
                        "type", "string",
                        "description", "Output format",
                        "enum", Arrays.asList("raw", "structured", "summary"),
                        "default", "structured"
                ),
                "tail", Map.of(
                        "type", "boolean",
                        "description", "Follow log file for new entries (returns immediately with current state)",
                        "default", false
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            int lines = ((Number) params.getOrDefault("lines", 100)).intValue();
            String level = (String) params.getOrDefault("level", "ALL");
            String pattern = (String) params.get("pattern");
            String logFile = (String) params.get("logFile");
            String since = (String) params.get("since");
            String until = (String) params.get("until");
            boolean includeStackTrace = (boolean) params.getOrDefault("includeStackTrace", true);
            boolean groupByThread = (boolean) params.getOrDefault("groupByThread", false);
            String format = (String) params.getOrDefault("format", "structured");
            boolean tail = (boolean) params.getOrDefault("tail", false);
            
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", System.currentTimeMillis());
            
            // Find log file
            Path logFilePath = findLogFile(logFile);
            result.put("logFile", logFilePath.toString());
            result.put("logFileSize", Files.size(logFilePath));
            result.put("logFileLastModified", Files.getLastModifiedTime(logFilePath).toInstant().toString());
            
            // Parse time filters
            LocalDateTime sinceTime = parseDateTime(since);
            LocalDateTime untilTime = parseDateTime(until);
            
            // Read and filter log entries
            List<LogEntry> logEntries = readLogEntries(logFilePath, lines, level, pattern, 
                    sinceTime, untilTime, includeStackTrace);
            
            result.put("totalEntries", logEntries.size());
            result.put("filters", getAppliedFilters(level, pattern, since, until));
            
            // Format output
            switch (format) {
                case "raw":
                    result.put("entries", logEntries.stream()
                            .map(LogEntry::getRawLine)
                            .collect(Collectors.toList()));
                    break;
                    
                case "summary":
                    result.put("summary", createLogSummary(logEntries));
                    result.put("recentEntries", logEntries.stream()
                            .limit(10)
                            .map(this::formatLogEntryBrief)
                            .collect(Collectors.toList()));
                    break;
                    
                case "structured":
                default:
                    if (groupByThread) {
                        result.put("entriesByThread", groupLogEntriesByThread(logEntries));
                    } else {
                        result.put("entries", logEntries.stream()
                                .map(this::formatLogEntry)
                                .collect(Collectors.toList()));
                    }
                    break;
            }
            
            // Add tail information if requested
            if (tail) {
                result.put("tailInfo", Map.of(
                        "message", "Log file monitoring would continue in a real implementation",
                        "currentPosition", Files.size(logFilePath),
                        "monitoringActive", false
                ));
            }
            
            return result;
            
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read log entries: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to read log entries: " + e.getMessage(), e);
        }
    }
    
    private Path findLogFile(String logFile) throws McpToolException, IOException {
        if (logFile != null && !logFile.trim().isEmpty()) {
            Path path = Paths.get(logFile);
            if (Files.exists(path)) {
                return path;
            } else {
                throw new McpToolException(getName(), "Specified log file does not exist: " + logFile);
            }
        }
        
        // Auto-detect log file
        List<Path> candidates = Arrays.asList(
                Paths.get("logs", "spring.log"),
                Paths.get("logs", "application.log"),
                Paths.get("target", "spring-boot-logger.log"),
                Paths.get("build", "logs", "spring.log"),
                Paths.get("spring-boot.log"),
                Paths.get("application.log")
        );
        
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        
        // Check for any .log files in common directories
        String[] logDirs = {"logs", "target", "build/logs", "."};
        for (String dir : logDirs) {
            Path dirPath = Paths.get(dir);
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                try (Stream<Path> files = Files.list(dirPath)) {
                    Optional<Path> logFileInDir = files
                            .filter(p -> p.toString().endsWith(".log"))
                            .findFirst();
                    if (logFileInDir.isPresent()) {
                        return logFileInDir.get();
                    }
                }
            }
        }
        
        throw new McpToolException(getName(), "No log file found. Specify logFile parameter or ensure logs directory exists.");
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            return LocalDateTime.parse(dateTimeStr.replace("T", " "), 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            log.warn("Could not parse datetime: {}. Expected format: yyyy-MM-ddTHH:mm:ss", dateTimeStr);
            return null;
        }
    }
    
    private List<LogEntry> readLogEntries(Path logFile, int maxLines, String levelFilter, 
                                        String pattern, LocalDateTime since, LocalDateTime until,
                                        boolean includeStackTrace) throws IOException {
        
        List<String> lines = Files.readAllLines(logFile);
        List<LogEntry> entries = new ArrayList<>();
        
        // Start from the end and work backwards
        int startIndex = Math.max(0, lines.size() - maxLines * 2); // Read more lines initially to account for filtering
        
        LogEntry currentEntry = null;
        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i);
            
            // Try to parse as new log entry
            LogEntry parsed = parseLogLine(line, i);
            if (parsed != null) {
                // Save previous entry if it exists
                if (currentEntry != null && shouldIncludeEntry(currentEntry, levelFilter, pattern, since, until)) {
                    entries.add(currentEntry);
                }
                currentEntry = parsed;
            } else if (currentEntry != null) {
                // This is likely a continuation line (stack trace, etc.)
                if (includeStackTrace) {
                    currentEntry.addContinuationLine(line);
                }
            }
        }
        
        // Don't forget the last entry
        if (currentEntry != null && shouldIncludeEntry(currentEntry, levelFilter, pattern, since, until)) {
            entries.add(currentEntry);
        }
        
        // Sort by timestamp and limit to requested number
        return entries.stream()
                .sorted((a, b) -> {
                    if (a.getTimestamp() != null && b.getTimestamp() != null) {
                        return a.getTimestamp().compareTo(b.getTimestamp());
                    }
                    return Integer.compare(a.getLineNumber(), b.getLineNumber());
                })
                .skip(Math.max(0, entries.size() - maxLines))
                .collect(Collectors.toList());
    }
    
    private LogEntry parseLogLine(String line, int lineNumber) {
        // Try Spring Boot pattern first
        Matcher matcher = SPRING_BOOT_PATTERN.matcher(line);
        if (matcher.matches()) {
            return createLogEntry(matcher, line, lineNumber, 1, 2, 3, 4, 5);
        }
        
        // Try standard log pattern
        matcher = LOG_PATTERN.matcher(line);
        if (matcher.matches()) {
            return createLogEntry(matcher, line, lineNumber, 1, 2, 3, 4, 5);
        }
        
        // Try simple pattern
        matcher = SIMPLE_PATTERN.matcher(line);
        if (matcher.matches()) {
            LogEntry entry = new LogEntry();
            entry.setRawLine(line);
            entry.setLineNumber(lineNumber);
            entry.setTimestamp(parseTimestamp(matcher.group(1)));
            entry.setLevel(matcher.group(2));
            entry.setMessage(matcher.group(3));
            return entry;
        }
        
        return null; // Not a log line, probably a continuation
    }
    
    private LogEntry createLogEntry(Matcher matcher, String line, int lineNumber,
                                  int timestampGroup, int threadGroup, int levelGroup, 
                                  int loggerGroup, int messageGroup) {
        LogEntry entry = new LogEntry();
        entry.setRawLine(line);
        entry.setLineNumber(lineNumber);
        entry.setTimestamp(parseTimestamp(matcher.group(timestampGroup)));
        entry.setThread(matcher.group(threadGroup));
        entry.setLevel(matcher.group(levelGroup));
        entry.setLogger(matcher.group(loggerGroup));
        entry.setMessage(matcher.group(messageGroup));
        return entry;
    }
    
    private LocalDateTime parseTimestamp(String timestampStr) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        
        log.debug("Could not parse timestamp: {}", timestampStr);
        return null;
    }
    
    private boolean shouldIncludeEntry(LogEntry entry, String levelFilter, String pattern,
                                     LocalDateTime since, LocalDateTime until) {
        // Level filter
        if (!"ALL".equals(levelFilter) && !levelFilter.equals(entry.getLevel())) {
            return false;
        }
        
        // Pattern filter
        if (pattern != null && !pattern.trim().isEmpty()) {
            try {
                Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                String searchText = entry.getMessage() + " " + (entry.getLogger() != null ? entry.getLogger() : "");
                if (!p.matcher(searchText).find()) {
                    return false;
                }
            } catch (Exception e) {
                // If regex fails, use simple string contains
                String searchText = entry.getMessage() + " " + (entry.getLogger() != null ? entry.getLogger() : "");
                if (!searchText.toLowerCase().contains(pattern.toLowerCase())) {
                    return false;
                }
            }
        }
        
        // Time filters
        if (entry.getTimestamp() != null) {
            if (since != null && entry.getTimestamp().isBefore(since)) {
                return false;
            }
            if (until != null && entry.getTimestamp().isAfter(until)) {
                return false;
            }
        }
        
        return true;
    }
    
    private Map<String, Object> getAppliedFilters(String level, String pattern, String since, String until) {
        Map<String, Object> filters = new HashMap<>();
        if (!"ALL".equals(level)) {
            filters.put("level", level);
        }
        if (pattern != null && !pattern.trim().isEmpty()) {
            filters.put("pattern", pattern);
        }
        if (since != null) {
            filters.put("since", since);
        }
        if (until != null) {
            filters.put("until", until);
        }
        return filters;
    }
    
    private Map<String, Object> createLogSummary(List<LogEntry> entries) {
        Map<String, Object> summary = new HashMap<>();
        
        Map<String, Long> levelCounts = entries.stream()
                .collect(Collectors.groupingBy(LogEntry::getLevel, Collectors.counting()));
        summary.put("levelCounts", levelCounts);
        
        Map<String, Long> loggerCounts = entries.stream()
                .filter(e -> e.getLogger() != null)
                .collect(Collectors.groupingBy(LogEntry::getLogger, Collectors.counting()));
        summary.put("topLoggers", loggerCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                )));
        
        if (!entries.isEmpty()) {
            summary.put("timeRange", Map.of(
                    "earliest", entries.stream()
                            .filter(e -> e.getTimestamp() != null)
                            .map(LogEntry::getTimestamp)
                            .min(LocalDateTime::compareTo)
                            .map(LocalDateTime::toString)
                            .orElse("unknown"),
                    "latest", entries.stream()
                            .filter(e -> e.getTimestamp() != null)
                            .map(LogEntry::getTimestamp)
                            .max(LocalDateTime::compareTo)
                            .map(LocalDateTime::toString)
                            .orElse("unknown")
            ));
        }
        
        // Error patterns
        long errorCount = entries.stream()
                .filter(e -> "ERROR".equals(e.getLevel()) || "FATAL".equals(e.getLevel()))
                .count();
        summary.put("errorCount", errorCount);
        
        return summary;
    }
    
    private Map<String, Object> formatLogEntry(LogEntry entry) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("timestamp", entry.getTimestamp() != null ? entry.getTimestamp().toString() : null);
        formatted.put("level", entry.getLevel());
        formatted.put("thread", entry.getThread());
        formatted.put("logger", entry.getLogger());
        formatted.put("message", entry.getMessage());
        formatted.put("lineNumber", entry.getLineNumber());
        
        if (!entry.getContinuationLines().isEmpty()) {
            formatted.put("continuationLines", entry.getContinuationLines());
        }
        
        return formatted;
    }
    
    private Map<String, Object> formatLogEntryBrief(LogEntry entry) {
        Map<String, Object> brief = new HashMap<>();
        brief.put("timestamp", entry.getTimestamp() != null ? entry.getTimestamp().toString() : null);
        brief.put("level", entry.getLevel());
        brief.put("message", truncateMessage(entry.getMessage(), 100));
        return brief;
    }
    
    private String truncateMessage(String message, int maxLength) {
        if (message == null || message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }
    
    private Map<String, List<Map<String, Object>>> groupLogEntriesByThread(List<LogEntry> entries) {
        return entries.stream()
                .filter(e -> e.getThread() != null)
                .collect(Collectors.groupingBy(
                        LogEntry::getThread,
                        Collectors.mapping(this::formatLogEntry, Collectors.toList())
                ));
    }
    
    // LogEntry data class
    private static class LogEntry {
        private String rawLine;
        private int lineNumber;
        private LocalDateTime timestamp;
        private String thread;
        private String level;
        private String logger;
        private String message;
        private List<String> continuationLines = new ArrayList<>();
        
        public void addContinuationLine(String line) {
            continuationLines.add(line);
        }
        
        // Getters and setters
        public String getRawLine() { return rawLine; }
        public void setRawLine(String rawLine) { this.rawLine = rawLine; }
        public int getLineNumber() { return lineNumber; }
        public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public String getThread() { return thread; }
        public void setThread(String thread) { this.thread = thread; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getLogger() { return logger; }
        public void setLogger(String logger) { this.logger = logger; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<String> getContinuationLines() { return continuationLines; }
        public void setContinuationLines(List<String> continuationLines) { this.continuationLines = continuationLines; }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "lastErrors", Map.of(
                        "description", "Get last error entries",
                        "parameters", Map.of(
                                "level", "ERROR",
                                "lines", 50
                        )
                ),
                "searchPattern", Map.of(
                        "description", "Search for specific pattern in logs",
                        "parameters", Map.of(
                                "pattern", "Exception|Error",
                                "lines", 200
                        )
                ),
                "timeRange", Map.of(
                        "description", "Get logs from specific time range",
                        "parameters", Map.of(
                                "since", "2023-12-01T10:00:00",
                                "until", "2023-12-01T12:00:00"
                        )
                ),
                "threadGrouped", Map.of(
                        "description", "Group log entries by thread",
                        "parameters", Map.of(
                                "groupByThread", true,
                                "lines", 100
                        )
                ),
                "summary", Map.of(
                        "description", "Get log summary with statistics",
                        "parameters", Map.of(
                                "format", "summary",
                                "lines", 1000
                        )
                )
        );
    }
}
