package com.springboost.mcp.tools.impl;

import com.springboost.config.SpringBoostProperties;
import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tool to execute safe SELECT queries against the database
 * Provides query execution with result formatting and security validation
 */
@Slf4j
@Component
public class DatabaseQueryTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    private final SpringBoostProperties properties;
    
    // Patterns for SQL validation
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*SELECT\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|EXEC|EXECUTE)\\b", 
            Pattern.CASE_INSENSITIVE);
    
    private static final int DEFAULT_MAX_ROWS = 100;
    private static final int ABSOLUTE_MAX_ROWS = 1000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    @Autowired
    public DatabaseQueryTool(ApplicationContext applicationContext, SpringBoostProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
    }
    
    @Override
    public String getName() {
        return "database-query";
    }
    
    @Override
    public String getDescription() {
        return "Execute safe SELECT queries against the database with result formatting and security validation";
    }
    
    @Override
    public String getCategory() {
        return "database";
    }
    
    @Override
    public boolean requiresElevatedPrivileges() {
        return true; // Database queries require elevated privileges
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "query", Map.of(
                        "type", "string",
                        "description", "SELECT query to execute (only SELECT statements are allowed)",
                        "minLength", 1
                ),
                "maxRows", Map.of(
                        "type", "integer",
                        "description", "Maximum number of rows to return (default: 100, max: 1000)",
                        "minimum", 1,
                        "maximum", ABSOLUTE_MAX_ROWS,
                        "default", DEFAULT_MAX_ROWS
                ),
                "timeout", Map.of(
                        "type", "integer",
                        "description", "Query timeout in seconds (default: 30)",
                        "minimum", 1,
                        "maximum", 300,
                        "default", DEFAULT_TIMEOUT_SECONDS
                ),
                "includeMetadata", Map.of(
                        "type", "boolean",
                        "description", "Include column metadata in the response",
                        "default", true
                ),
                "format", Map.of(
                        "type", "string",
                        "description", "Result format: 'table' (default) or 'json'",
                        "enum", Arrays.asList("table", "json"),
                        "default", "table"
                )
        ));
        schema.put("required", Arrays.asList("query"));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String query = (String) params.get("query");
            if (query == null || query.trim().isEmpty()) {
                throw new McpToolException(getName(), "Query parameter is required and cannot be empty");
            }
            
            int maxRows = ((Number) params.getOrDefault("maxRows", DEFAULT_MAX_ROWS)).intValue();
            int timeout = ((Number) params.getOrDefault("timeout", DEFAULT_TIMEOUT_SECONDS)).intValue();
            boolean includeMetadata = (boolean) params.getOrDefault("includeMetadata", true);
            String format = (String) params.getOrDefault("format", "table");
            
            // Validate query
            validateQuery(query);
            
            // Get DataSource
            DataSource dataSource = getPrimaryDataSource();
            if (dataSource == null) {
                throw new McpToolException(getName(), "No DataSource found in application context");
            }
            
            // Execute query
            return executeQuery(dataSource, query, maxRows, timeout, includeMetadata, format);
            
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute database query: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to execute database query: " + e.getMessage(), e);
        }
    }
    
    private void validateQuery(String query) throws McpToolException {
        String normalizedQuery = query.trim();
        
        // Check if it's a SELECT statement
        if (!SELECT_PATTERN.matcher(normalizedQuery).find()) {
            throw new McpToolException(getName(), "Only SELECT queries are allowed");
        }
        
        // Check for dangerous patterns
        if (DANGEROUS_PATTERNS.matcher(normalizedQuery).find()) {
            throw new McpToolException(getName(), "Query contains potentially dangerous SQL keywords");
        }
        
        // Additional security checks
        if (normalizedQuery.toLowerCase().contains(";")) {
            // Allow semicolon only at the end
            String withoutTrailingSemicolon = normalizedQuery.replaceAll(";\\s*$", "");
            if (withoutTrailingSemicolon.contains(";")) {
                throw new McpToolException(getName(), "Multiple statements are not allowed");
            }
        }
        
        // Check for common SQL injection patterns
        String lowerQuery = normalizedQuery.toLowerCase();
        if (lowerQuery.contains("--") || lowerQuery.contains("/*") || lowerQuery.contains("*/")) {
            throw new McpToolException(getName(), "SQL comments are not allowed");
        }
    }
    
    private DataSource getPrimaryDataSource() {
        Map<String, DataSource> dataSources = applicationContext.getBeansOfType(DataSource.class);
        
        if (dataSources.isEmpty()) {
            return null;
        }
        
        // Try to find primary DataSource
        if (dataSources.containsKey("dataSource")) {
            return dataSources.get("dataSource");
        }
        
        // Return the first one found
        return dataSources.values().iterator().next();
    }
    
    private Map<String, Object> executeQuery(DataSource dataSource, String query, int maxRows, 
                                           int timeout, boolean includeMetadata, String format) throws SQLException {
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            
            // Set query timeout
            statement.setQueryTimeout(timeout);
            statement.setMaxRows(maxRows);
            
            log.debug("Executing query: {}", query);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                // Column metadata
                List<Map<String, Object>> columns = new ArrayList<>();
                if (includeMetadata) {
                    for (int i = 1; i <= columnCount; i++) {
                        Map<String, Object> column = new HashMap<>();
                        column.put("name", metaData.getColumnName(i));
                        column.put("label", metaData.getColumnLabel(i));
                        column.put("type", metaData.getColumnTypeName(i));
                        column.put("sqlType", metaData.getColumnType(i));
                        column.put("precision", metaData.getPrecision(i));
                        column.put("scale", metaData.getScale(i));
                        column.put("nullable", metaData.isNullable(i) != ResultSetMetaData.columnNoNulls);
                        column.put("autoIncrement", metaData.isAutoIncrement(i));
                        columns.add(column);
                    }
                    result.put("columns", columns);
                }
                
                // Data rows
                List<Object> rows = new ArrayList<>();
                int rowCount = 0;
                
                if ("json".equals(format)) {
                    // JSON format - each row as an object
                    while (resultSet.next() && rowCount < maxRows) {
                        Map<String, Object> row = new HashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnLabel(i);
                            Object value = getColumnValue(resultSet, i, metaData.getColumnType(i));
                            row.put(columnName, value);
                        }
                        rows.add(row);
                        rowCount++;
                    }
                } else {
                    // Table format - each row as an array
                    while (resultSet.next() && rowCount < maxRows) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = getColumnValue(resultSet, i, metaData.getColumnType(i));
                            row.add(value);
                        }
                        rows.add(row);
                        rowCount++;
                    }
                }
                
                result.put("data", rows);
                result.put("rowCount", rowCount);
                result.put("hasMoreRows", resultSet.next()); // Check if there are more rows
                
                // Column names for table format
                if ("table".equals(format)) {
                    List<String> columnNames = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columnNames.add(metaData.getColumnLabel(i));
                    }
                    result.put("columnNames", columnNames);
                }
                
                // Execution statistics
                long executionTime = System.currentTimeMillis() - startTime;
                Map<String, Object> stats = new HashMap<>();
                stats.put("executionTimeMs", executionTime);
                stats.put("columnCount", columnCount);
                stats.put("rowsReturned", rowCount);
                stats.put("maxRowsLimit", maxRows);
                stats.put("timeoutSeconds", timeout);
                stats.put("format", format);
                result.put("executionStats", stats);
                
                result.put("success", true);
                result.put("query", query);
                result.put("timestamp", System.currentTimeMillis());
                
                log.debug("Query executed successfully in {}ms, returned {} rows", executionTime, rowCount);
                
                return result;
            }
        }
    }
    
    private Object getColumnValue(ResultSet resultSet, int columnIndex, int sqlType) throws SQLException {
        Object value = resultSet.getObject(columnIndex);
        
        if (value == null) {
            return null;
        }
        
        // Handle specific SQL types for better JSON serialization
        switch (sqlType) {
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return value.toString(); // Convert to string for JSON compatibility
                
            case Types.BLOB:
            case Types.CLOB:
                return "[BLOB/CLOB]"; // Don't return actual blob content
                
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return "[BINARY]"; // Don't return binary content
                
            default:
                return value;
        }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "simpleSelect", Map.of(
                        "description", "Execute a simple SELECT query",
                        "parameters", Map.of("query", "SELECT * FROM users LIMIT 10")
                ),
                "withLimit", Map.of(
                        "description", "Execute query with custom row limit",
                        "parameters", Map.of(
                                "query", "SELECT id, name, email FROM users WHERE active = true",
                                "maxRows", 50
                        )
                ),
                "jsonFormat", Map.of(
                        "description", "Return results in JSON format",
                        "parameters", Map.of(
                                "query", "SELECT count(*) as user_count FROM users",
                                "format", "json"
                        )
                ),
                "withTimeout", Map.of(
                        "description", "Execute with custom timeout",
                        "parameters", Map.of(
                                "query", "SELECT * FROM large_table WHERE complex_condition = 'value'",
                                "timeout", 60,
                                "maxRows", 20
                        )
                ),
                "aggregation", Map.of(
                        "description", "Execute aggregation query",
                        "parameters", Map.of(
                                "query", "SELECT status, COUNT(*) as count FROM orders GROUP BY status",
                                "includeMetadata", false
                        )
                )
        );
    }
}
