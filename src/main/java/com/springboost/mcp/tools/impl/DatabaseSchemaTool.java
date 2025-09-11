package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Attribute;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Tool to inspect database schema and JPA entity mappings
 * Provides information about tables, columns, relationships, and JPA entity definitions
 */
@Slf4j
@Component
public class DatabaseSchemaTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    
    @Autowired
    public DatabaseSchemaTool(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public String getName() {
        return "database-schema";
    }
    
    @Override
    public String getDescription() {
        return "Inspect database schema, tables, columns, indexes, foreign keys, and JPA entity mappings";
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
                "includeColumns", Map.of(
                        "type", "boolean",
                        "description", "Include detailed column information",
                        "default", true
                ),
                "includeIndexes", Map.of(
                        "type", "boolean",
                        "description", "Include index information",
                        "default", true
                ),
                "includeForeignKeys", Map.of(
                        "type", "boolean",
                        "description", "Include foreign key relationships",
                        "default", true
                ),
                "includeJpaEntities", Map.of(
                        "type", "boolean",
                        "description", "Include JPA entity information",
                        "default", true
                ),
                "tableNameFilter", Map.of(
                        "type", "string",
                        "description", "Filter tables by name (case-insensitive substring match)"
                ),
                "schemaName", Map.of(
                        "type", "string",
                        "description", "Specific schema to inspect (if not provided, uses default)"
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            boolean includeColumns = (boolean) params.getOrDefault("includeColumns", true);
            boolean includeIndexes = (boolean) params.getOrDefault("includeIndexes", true);
            boolean includeForeignKeys = (boolean) params.getOrDefault("includeForeignKeys", true);
            boolean includeJpaEntities = (boolean) params.getOrDefault("includeJpaEntities", true);
            String tableNameFilter = (String) params.get("tableNameFilter");
            String schemaName = (String) params.get("schemaName");
            
            Map<String, Object> result = new HashMap<>();
            
            // Get primary DataSource
            DataSource dataSource = getPrimaryDataSource();
            if (dataSource == null) {
                throw new McpToolException(getName(), "No DataSource found in application context");
            }
            
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                
                // Database information
                result.put("databaseInfo", getDatabaseInfo(metaData));
                
                // Schema information
                if (schemaName == null) {
                    schemaName = connection.getSchema();
                }
                result.put("schemaName", schemaName);
                
                // Get tables
                List<Map<String, Object>> tables = getTables(metaData, schemaName, tableNameFilter);
                
                // Enhance tables with detailed information
                for (Map<String, Object> table : tables) {
                    String tableName = (String) table.get("tableName");
                    
                    if (includeColumns) {
                        table.put("columns", getColumns(metaData, schemaName, tableName));
                    }
                    
                    if (includeIndexes) {
                        table.put("indexes", getIndexes(metaData, schemaName, tableName));
                    }
                    
                    if (includeForeignKeys) {
                        table.put("foreignKeys", getForeignKeys(metaData, schemaName, tableName));
                        table.put("exportedKeys", getExportedKeys(metaData, schemaName, tableName));
                    }
                }
                
                result.put("tables", tables);
                result.put("tableCount", tables.size());
                
                // JPA Entity information
                if (includeJpaEntities) {
                    result.put("jpaEntities", getJpaEntityInfo());
                }
                
                result.put("timestamp", System.currentTimeMillis());
                
                return result;
            }
            
        } catch (Exception e) {
            log.error("Failed to inspect database schema: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to inspect database schema: " + e.getMessage(), e);
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
    
    private Map<String, Object> getDatabaseInfo(DatabaseMetaData metaData) throws SQLException {
        Map<String, Object> info = new HashMap<>();
        info.put("productName", metaData.getDatabaseProductName());
        info.put("productVersion", metaData.getDatabaseProductVersion());
        info.put("majorVersion", metaData.getDatabaseMajorVersion());
        info.put("minorVersion", metaData.getDatabaseMinorVersion());
        info.put("catalogSeparator", metaData.getCatalogSeparator());
        info.put("catalogTerm", metaData.getCatalogTerm());
        info.put("schemaTerm", metaData.getSchemaTerm());
        info.put("procedureTerm", metaData.getProcedureTerm());
        info.put("identifierQuoteString", metaData.getIdentifierQuoteString());
        return info;
    }
    
    private List<Map<String, Object>> getTables(DatabaseMetaData metaData, String schemaName, String nameFilter) throws SQLException {
        List<Map<String, Object>> tables = new ArrayList<>();
        
        try (ResultSet rs = metaData.getTables(null, schemaName, null, new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                
                // Apply name filter if provided
                if (nameFilter != null && !nameFilter.isEmpty() && 
                    !tableName.toLowerCase().contains(nameFilter.toLowerCase())) {
                    continue;
                }
                
                Map<String, Object> table = new HashMap<>();
                table.put("tableName", tableName);
                table.put("tableType", rs.getString("TABLE_TYPE"));
                table.put("schema", rs.getString("TABLE_SCHEM"));
                table.put("catalog", rs.getString("TABLE_CAT"));
                table.put("remarks", rs.getString("REMARKS"));
                
                tables.add(table);
            }
        }
        
        return tables;
    }
    
    private List<Map<String, Object>> getColumns(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        List<Map<String, Object>> columns = new ArrayList<>();
        
        try (ResultSet rs = metaData.getColumns(null, schemaName, tableName, null)) {
            while (rs.next()) {
                Map<String, Object> column = new HashMap<>();
                column.put("columnName", rs.getString("COLUMN_NAME"));
                column.put("dataType", rs.getString("TYPE_NAME"));
                column.put("sqlType", rs.getInt("DATA_TYPE"));
                column.put("columnSize", rs.getInt("COLUMN_SIZE"));
                column.put("decimalDigits", rs.getInt("DECIMAL_DIGITS"));
                column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                column.put("remarks", rs.getString("REMARKS"));
                column.put("defaultValue", rs.getString("COLUMN_DEF"));
                column.put("position", rs.getInt("ORDINAL_POSITION"));
                column.put("autoIncrement", "YES".equals(rs.getString("IS_AUTOINCREMENT")));
                
                columns.add(column);
            }
        }
        
        return columns;
    }
    
    private List<Map<String, Object>> getIndexes(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        List<Map<String, Object>> indexes = new ArrayList<>();
        Map<String, Map<String, Object>> indexMap = new HashMap<>();
        
        try (ResultSet rs = metaData.getIndexInfo(null, schemaName, tableName, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) continue; // Skip table statistics
                
                Map<String, Object> index = indexMap.get(indexName);
                if (index == null) {
                    index = new HashMap<>();
                    index.put("indexName", indexName);
                    index.put("unique", !rs.getBoolean("NON_UNIQUE"));
                    index.put("type", rs.getShort("TYPE"));
                    index.put("columns", new ArrayList<Map<String, Object>>());
                    indexMap.put(indexName, index);
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columns = (List<Map<String, Object>>) index.get("columns");
                
                Map<String, Object> column = new HashMap<>();
                column.put("columnName", rs.getString("COLUMN_NAME"));
                column.put("position", rs.getShort("ORDINAL_POSITION"));
                column.put("sortOrder", rs.getString("ASC_OR_DESC"));
                columns.add(column);
            }
        }
        
        indexes.addAll(indexMap.values());
        return indexes;
    }
    
    private List<Map<String, Object>> getForeignKeys(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        List<Map<String, Object>> foreignKeys = new ArrayList<>();
        
        try (ResultSet rs = metaData.getImportedKeys(null, schemaName, tableName)) {
            while (rs.next()) {
                Map<String, Object> fk = new HashMap<>();
                fk.put("constraintName", rs.getString("FK_NAME"));
                fk.put("columnName", rs.getString("FKCOLUMN_NAME"));
                fk.put("referencedTable", rs.getString("PKTABLE_NAME"));
                fk.put("referencedColumn", rs.getString("PKCOLUMN_NAME"));
                fk.put("referencedSchema", rs.getString("PKTABLE_SCHEM"));
                fk.put("updateRule", getForeignKeyRuleName(rs.getShort("UPDATE_RULE")));
                fk.put("deleteRule", getForeignKeyRuleName(rs.getShort("DELETE_RULE")));
                fk.put("deferrability", rs.getShort("DEFERRABILITY"));
                
                foreignKeys.add(fk);
            }
        }
        
        return foreignKeys;
    }
    
    private List<Map<String, Object>> getExportedKeys(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        List<Map<String, Object>> exportedKeys = new ArrayList<>();
        
        try (ResultSet rs = metaData.getExportedKeys(null, schemaName, tableName)) {
            while (rs.next()) {
                Map<String, Object> ek = new HashMap<>();
                ek.put("constraintName", rs.getString("FK_NAME"));
                ek.put("columnName", rs.getString("PKCOLUMN_NAME"));
                ek.put("referencingTable", rs.getString("FKTABLE_NAME"));
                ek.put("referencingColumn", rs.getString("FKCOLUMN_NAME"));
                ek.put("referencingSchema", rs.getString("FKTABLE_SCHEM"));
                
                exportedKeys.add(ek);
            }
        }
        
        return exportedKeys;
    }
    
    private String getForeignKeyRuleName(short rule) {
        switch (rule) {
            case DatabaseMetaData.importedKeyCascade:
                return "CASCADE";
            case DatabaseMetaData.importedKeyRestrict:
                return "RESTRICT";
            case DatabaseMetaData.importedKeySetNull:
                return "SET NULL";
            case DatabaseMetaData.importedKeySetDefault:
                return "SET DEFAULT";
            case DatabaseMetaData.importedKeyNoAction:
                return "NO ACTION";
            default:
                return "UNKNOWN (" + rule + ")";
        }
    }
    
    private Map<String, Object> getJpaEntityInfo() {
        Map<String, Object> jpaInfo = new HashMap<>();
        
        try {
            // Try to find EntityManagerFactory
            Map<String, EntityManagerFactory> emFactories = applicationContext.getBeansOfType(EntityManagerFactory.class);
            
            if (emFactories.isEmpty()) {
                jpaInfo.put("message", "No EntityManagerFactory found - JPA not configured");
                return jpaInfo;
            }
            
            EntityManagerFactory emf = emFactories.values().iterator().next();
            EntityManager em = emf.createEntityManager();
            
            try {
                Set<EntityType<?>> entityTypes = em.getMetamodel().getEntities();
                List<Map<String, Object>> entities = new ArrayList<>();
                
                for (EntityType<?> entityType : entityTypes) {
                    Map<String, Object> entity = new HashMap<>();
                    entity.put("entityName", entityType.getName());
                    entity.put("javaType", entityType.getJavaType().getName());
                    
                    // Attributes
                    List<Map<String, Object>> attributes = new ArrayList<>();
                    for (Attribute<?, ?> attribute : entityType.getAttributes()) {
                        Map<String, Object> attr = new HashMap<>();
                        attr.put("name", attribute.getName());
                        attr.put("javaType", attribute.getJavaType().getSimpleName());
                        attr.put("persistentAttributeType", attribute.getPersistentAttributeType().toString());
                        attr.put("collection", attribute.isCollection());
                        attr.put("association", attribute.isAssociation());
                        attributes.add(attr);
                    }
                    entity.put("attributes", attributes);
                    entity.put("attributeCount", attributes.size());
                    
                    entities.add(entity);
                }
                
                jpaInfo.put("entities", entities);
                jpaInfo.put("entityCount", entities.size());
                
            } finally {
                em.close();
            }
            
        } catch (Exception e) {
            log.debug("Could not retrieve JPA entity information: {}", e.getMessage());
            jpaInfo.put("error", "Could not retrieve JPA entity information: " + e.getMessage());
        }
        
        return jpaInfo;
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "basic", Map.of(
                        "description", "Get basic schema information with all details",
                        "parameters", Map.of()
                ),
                "tablesOnly", Map.of(
                        "description", "Get only table names without detailed column information",
                        "parameters", Map.of(
                                "includeColumns", false,
                                "includeIndexes", false,
                                "includeForeignKeys", false
                        )
                ),
                "filteredTables", Map.of(
                        "description", "Get tables matching a name filter",
                        "parameters", Map.of("tableNameFilter", "user")
                ),
                "jpaOnly", Map.of(
                        "description", "Get only JPA entity information",
                        "parameters", Map.of(
                                "includeColumns", false,
                                "includeIndexes", false,
                                "includeForeignKeys", false,
                                "includeJpaEntities", true
                        )
                ),
                "specificSchema", Map.of(
                        "description", "Inspect a specific database schema",
                        "parameters", Map.of("schemaName", "PUBLIC")
                )
        );
    }
}
