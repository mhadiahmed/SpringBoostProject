package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * Tool to inspect database connections and DataSource configurations
 * Provides information about connection pools, database metadata, and active connections
 */
@Slf4j
@Component
public class DatabaseConnectionsTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    
    @Autowired
    public DatabaseConnectionsTool(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public String getName() {
        return "database-connections";
    }
    
    @Override
    public String getDescription() {
        return "Inspect database connections, DataSource configurations, and connection pool information";
    }
    
    @Override
    public String getCategory() {
        return "database";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "includeMetadata", Map.of(
                        "type", "boolean",
                        "description", "Include database metadata information",
                        "default", true
                ),
                "includePoolStats", Map.of(
                        "type", "boolean",
                        "description", "Include connection pool statistics",
                        "default", true
                ),
                "testConnections", Map.of(
                        "type", "boolean",
                        "description", "Test database connections (may be slow)",
                        "default", false
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            boolean includeMetadata = (boolean) params.getOrDefault("includeMetadata", true);
            boolean includePoolStats = (boolean) params.getOrDefault("includePoolStats", true);
            boolean testConnections = (boolean) params.getOrDefault("testConnections", false);
            
            Map<String, Object> result = new HashMap<>();
            
            // Find all DataSource beans
            Map<String, DataSource> dataSources = applicationContext.getBeansOfType(DataSource.class);
            
            if (dataSources.isEmpty()) {
                result.put("message", "No DataSource beans found in application context");
                result.put("dataSourceCount", 0);
                return result;
            }
            
            List<Map<String, Object>> dataSourceInfoList = new ArrayList<>();
            
            for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
                String beanName = entry.getKey();
                DataSource dataSource = entry.getValue();
                
                Map<String, Object> dsInfo = new HashMap<>();
                dsInfo.put("beanName", beanName);
                dsInfo.put("className", dataSource.getClass().getSimpleName());
                dsInfo.put("fullClassName", dataSource.getClass().getName());
                
                // Basic DataSource information
                try {
                    dsInfo.put("basicInfo", getBasicDataSourceInfo(dataSource));
                } catch (Exception e) {
                    log.warn("Failed to get basic info for DataSource {}: {}", beanName, e.getMessage());
                    dsInfo.put("basicInfoError", e.getMessage());
                }
                
                // HikariCP specific information
                if (dataSource instanceof HikariDataSource) {
                    dsInfo.put("hikariInfo", getHikariDataSourceInfo((HikariDataSource) dataSource, includePoolStats));
                }
                
                // Database metadata
                if (includeMetadata) {
                    try {
                        dsInfo.put("metadata", getDatabaseMetadata(dataSource));
                    } catch (Exception e) {
                        log.warn("Failed to get metadata for DataSource {}: {}", beanName, e.getMessage());
                        dsInfo.put("metadataError", e.getMessage());
                    }
                }
                
                // Connection test
                if (testConnections) {
                    dsInfo.put("connectionTest", testConnection(dataSource));
                }
                
                dataSourceInfoList.add(dsInfo);
            }
            
            result.put("dataSourceCount", dataSources.size());
            result.put("dataSources", dataSourceInfoList);
            result.put("timestamp", System.currentTimeMillis());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to inspect database connections: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to inspect database connections: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> getBasicDataSourceInfo(DataSource dataSource) throws SQLException {
        Map<String, Object> info = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            info.put("catalog", connection.getCatalog());
            info.put("schema", connection.getSchema());
            info.put("autoCommit", connection.getAutoCommit());
            info.put("readOnly", connection.isReadOnly());
            info.put("transactionIsolation", getTransactionIsolationName(connection.getTransactionIsolation()));
        }
        
        return info;
    }
    
    private Map<String, Object> getHikariDataSourceInfo(HikariDataSource hikariDS, boolean includePoolStats) {
        Map<String, Object> info = new HashMap<>();
        
        // Configuration information
        info.put("poolName", hikariDS.getPoolName());
        info.put("jdbcUrl", hikariDS.getJdbcUrl());
        info.put("username", hikariDS.getUsername());
        info.put("driverClassName", hikariDS.getDriverClassName());
        info.put("maximumPoolSize", hikariDS.getMaximumPoolSize());
        info.put("minimumIdle", hikariDS.getMinimumIdle());
        info.put("connectionTimeout", hikariDS.getConnectionTimeout());
        info.put("idleTimeout", hikariDS.getIdleTimeout());
        info.put("maxLifetime", hikariDS.getMaxLifetime());
        info.put("leakDetectionThreshold", hikariDS.getLeakDetectionThreshold());
        
        // Pool statistics (if requested and available)
        if (includePoolStats) {
            try {
                HikariPoolMXBean poolBean = hikariDS.getHikariPoolMXBean();
                if (poolBean != null) {
                    Map<String, Object> poolStats = new HashMap<>();
                    poolStats.put("activeConnections", poolBean.getActiveConnections());
                    poolStats.put("idleConnections", poolBean.getIdleConnections());
                    poolStats.put("totalConnections", poolBean.getTotalConnections());
                    poolStats.put("threadsAwaitingConnection", poolBean.getThreadsAwaitingConnection());
                    info.put("poolStatistics", poolStats);
                }
            } catch (Exception e) {
                log.debug("Could not retrieve pool statistics: {}", e.getMessage());
            }
        }
        
        return info;
    }
    
    private Map<String, Object> getDatabaseMetadata(DataSource dataSource) throws SQLException {
        Map<String, Object> metadata = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData dbMetaData = connection.getMetaData();
            
            metadata.put("databaseProductName", dbMetaData.getDatabaseProductName());
            metadata.put("databaseProductVersion", dbMetaData.getDatabaseProductVersion());
            metadata.put("driverName", dbMetaData.getDriverName());
            metadata.put("driverVersion", dbMetaData.getDriverVersion());
            metadata.put("driverMajorVersion", dbMetaData.getDriverMajorVersion());
            metadata.put("driverMinorVersion", dbMetaData.getDriverMinorVersion());
            metadata.put("jdbcMajorVersion", dbMetaData.getJDBCMajorVersion());
            metadata.put("jdbcMinorVersion", dbMetaData.getJDBCMinorVersion());
            metadata.put("url", dbMetaData.getURL());
            metadata.put("userName", dbMetaData.getUserName());
            metadata.put("readOnly", dbMetaData.isReadOnly());
            metadata.put("supportsTransactions", dbMetaData.supportsTransactions());
            metadata.put("supportsSelectForUpdate", dbMetaData.supportsSelectForUpdate());
            metadata.put("supportsStoredProcedures", dbMetaData.supportsStoredProcedures());
            metadata.put("maxConnections", dbMetaData.getMaxConnections());
            metadata.put("maxStatements", dbMetaData.getMaxStatements());
            
            // SQL keywords
            try {
                metadata.put("sqlKeywords", Arrays.asList(dbMetaData.getSQLKeywords().split(",")));
            } catch (Exception e) {
                log.debug("Could not retrieve SQL keywords: {}", e.getMessage());
            }
        }
        
        return metadata;
    }
    
    private Map<String, Object> testConnection(DataSource dataSource) {
        Map<String, Object> testResult = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            long connectTime = System.currentTimeMillis() - startTime;
            
            testResult.put("success", true);
            testResult.put("connectionTimeMs", connectTime);
            testResult.put("valid", connection.isValid(5)); // 5 second timeout
            testResult.put("closed", connection.isClosed());
            
        } catch (Exception e) {
            long failTime = System.currentTimeMillis() - startTime;
            testResult.put("success", false);
            testResult.put("error", e.getMessage());
            testResult.put("errorClass", e.getClass().getSimpleName());
            testResult.put("failureTimeMs", failTime);
        }
        
        return testResult;
    }
    
    private String getTransactionIsolationName(int level) {
        switch (level) {
            case Connection.TRANSACTION_NONE:
                return "TRANSACTION_NONE";
            case Connection.TRANSACTION_READ_UNCOMMITTED:
                return "TRANSACTION_READ_UNCOMMITTED";
            case Connection.TRANSACTION_READ_COMMITTED:
                return "TRANSACTION_READ_COMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ:
                return "TRANSACTION_REPEATABLE_READ";
            case Connection.TRANSACTION_SERIALIZABLE:
                return "TRANSACTION_SERIALIZABLE";
            default:
                return "UNKNOWN (" + level + ")";
        }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "basic", Map.of(
                        "description", "Get basic database connection information",
                        "parameters", Map.of()
                ),
                "withTests", Map.of(
                        "description", "Include connection tests (may be slow)",
                        "parameters", Map.of("testConnections", true)
                ),
                "minimal", Map.of(
                        "description", "Get minimal information without metadata",
                        "parameters", Map.of(
                                "includeMetadata", false,
                                "includePoolStats", false
                        )
                ),
                "poolStatsOnly", Map.of(
                        "description", "Focus on connection pool statistics",
                        "parameters", Map.of(
                                "includeMetadata", false,
                                "includePoolStats", true
                        )
                )
        );
    }
}
