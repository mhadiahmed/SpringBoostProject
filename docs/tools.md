# Tool Reference

Spring Boost provides 15+ specialized MCP tools designed to accelerate AI-assisted Spring Boot development. Each tool provides specific functionality to help AI understand and work with your Spring Boot application.

## Tool Categories

- **[Application](#application-tools)** - Application information and configuration
- **[Database](#database-tools)** - Database inspection and querying
- **[Web](#web-tools)** - Web layer and endpoint management
- **[Logging](#logging-tools)** - Log analysis and error tracking
- **[Monitoring](#monitoring-tools)** - Application health and metrics
- **[Execution](#execution-tools)** - Code execution and testing
- **[Documentation](#documentation-tools)** - Documentation search and management

---

## Application Tools

### Application Info Tool

**Name**: `application-info`  
**Description**: Provides comprehensive information about the Spring Boot application

#### Parameters
None required.

#### Response
```json
{
  "springBootVersion": "3.2.0",
  "javaVersion": "17.0.8",
  "profiles": ["development", "h2"],
  "properties": {
    "server.port": 8080,
    "spring.application.name": "my-app"
  },
  "beans": ["applicationInfoTool", "dataSource", "userController"],
  "endpoints": 15,
  "buildInfo": {
    "version": "1.0.0-SNAPSHOT",
    "time": "2024-01-15T10:30:00Z"
  }
}
```

#### Use Cases
- Understanding application structure for AI code generation
- Debugging configuration issues
- Getting context for development decisions

---

## Database Tools

### Database Connections Tool

**Name**: `database-connections`  
**Description**: Inspects DataSource configurations and connection pool information

#### Parameters
- `includePoolStats` (boolean, optional): Include connection pool statistics

#### Response
```json
{
  "dataSources": [
    {
      "name": "primary",
      "url": "jdbc:h2:mem:testdb",
      "driverClassName": "org.h2.Driver",
      "poolSize": {
        "active": 2,
        "idle": 8,
        "max": 10
      },
      "status": "healthy"
    }
  ]
}
```

#### Use Cases
- Database connectivity troubleshooting
- Performance optimization
- Understanding data source configuration

### Database Schema Tool

**Name**: `database-schema`  
**Description**: Reads database schema and JPA entity mappings

#### Parameters
- `includeIndexes` (boolean, optional): Include index information
- `includeConstraints` (boolean, optional): Include constraint details

#### Response
```json
{
  "tables": [
    {
      "name": "users",
      "columns": [
        {
          "name": "id",
          "type": "BIGINT",
          "nullable": false,
          "primaryKey": true
        },
        {
          "name": "email",
          "type": "VARCHAR(255)",
          "nullable": false,
          "unique": true
        }
      ],
      "indexes": ["idx_users_email"],
      "foreignKeys": []
    }
  ],
  "entities": [
    {
      "className": "com.example.User",
      "tableName": "users",
      "relationships": ["OneToMany:orders"]
    }
  ]
}
```

#### Use Cases
- Understanding database structure for query generation
- Entity relationship mapping
- Database migration planning

### Database Query Tool

**Name**: `database-query`  
**Description**: Executes safe SELECT queries against the database

#### Parameters
- `sql` (string, required): SELECT query to execute
- `maxRows` (integer, optional): Maximum rows to return (default: 100)

#### Example
```json
{
  "sql": "SELECT id, email FROM users WHERE active = true LIMIT 10",
  "maxRows": 10
}
```

#### Response
```json
{
  "query": "SELECT id, email FROM users WHERE active = true LIMIT 10",
  "executionTime": 15,
  "rowCount": 7,
  "columns": ["id", "email"],
  "data": [
    {"id": 1, "email": "john@example.com"},
    {"id": 2, "email": "jane@example.com"}
  ]
}
```

#### Security
- Only SELECT statements allowed
- Query validation and sanitization
- Configurable row limits
- Read-only database access

---

## Web Tools

### List Endpoints Tool

**Name**: `list-endpoints`  
**Description**: Inspects REST endpoints and their mappings

#### Parameters
- `includeParameters` (boolean, optional): Include parameter details
- `includeSecurity` (boolean, optional): Include security information

#### Response
```json
{
  "endpoints": [
    {
      "path": "/api/users",
      "methods": ["GET", "POST"],
      "controller": "UserController",
      "parameters": [
        {
          "name": "page",
          "type": "int",
          "required": false
        }
      ],
      "security": ["ROLE_USER"],
      "produces": ["application/json"],
      "consumes": ["application/json"]
    }
  ]
}
```

#### Use Cases
- API documentation generation
- Endpoint testing and validation
- Security configuration review

### Get Absolute URL Tool

**Name**: `get-absolute-url`  
**Description**: Converts relative paths to absolute URLs

#### Parameters
- `path` (string, required): Relative path to convert

#### Example
```json
{
  "path": "/api/users/123"
}
```

#### Response
```json
{
  "relativePath": "/api/users/123",
  "absoluteUrl": "http://localhost:8080/api/users/123",
  "serverInfo": {
    "host": "localhost",
    "port": 8080,
    "contextPath": "",
    "protocol": "http"
  }
}
```

---

## Logging Tools

### Last Error Tool

**Name**: `last-error`  
**Description**: Reads the last error from application log files

#### Parameters
- `maxLines` (integer, optional): Maximum lines to analyze (default: 1000)
- `severity` (string, optional): Minimum severity level (ERROR, WARN, INFO)

#### Response
```json
{
  "timestamp": "2024-01-15T10:45:30.123Z",
  "level": "ERROR",
  "logger": "com.example.UserService",
  "message": "Failed to save user",
  "exception": {
    "type": "DataIntegrityViolationException",
    "message": "Duplicate entry 'john@example.com' for key 'email'",
    "stackTrace": "..."
  },
  "context": {
    "thread": "http-nio-8080-exec-1",
    "mdc": {"userId": "123", "requestId": "abc-def"}
  }
}
```

#### Use Cases
- Quick error diagnosis
- Understanding application failures
- Debugging production issues

### Read Log Entries Tool

**Name**: `read-log-entries`  
**Description**: Reads recent log entries with filtering capabilities

#### Parameters
- `count` (integer, optional): Number of entries to return (default: 50)
- `level` (string, optional): Filter by log level
- `logger` (string, optional): Filter by logger name
- `since` (string, optional): ISO timestamp to filter from

#### Response
```json
{
  "entries": [
    {
      "timestamp": "2024-01-15T10:45:30.123Z",
      "level": "INFO",
      "logger": "com.example.UserController",
      "message": "User created successfully",
      "thread": "http-nio-8080-exec-1"
    }
  ],
  "totalCount": 150,
  "filteredCount": 50
}
```

---

## Monitoring Tools

### Browser Logs Tool

**Name**: `browser-logs`  
**Description**: Collects and analyzes browser console logs and errors

#### Parameters
- `includeNetworkErrors` (boolean, optional): Include network request failures
- `severity` (string, optional): Minimum severity (error, warn, info)

#### Response
```json
{
  "consoleErrors": [
    {
      "timestamp": "2024-01-15T10:45:30.123Z",
      "level": "error",
      "message": "TypeError: Cannot read property 'id' of undefined",
      "source": "app.js:45:12",
      "stack": "..."
    }
  ],
  "networkErrors": [
    {
      "url": "/api/users/456",
      "status": 404,
      "timestamp": "2024-01-15T10:45:25.100Z"
    }
  ]
}
```

### List Actuator Endpoints Tool

**Name**: `list-actuator-endpoints`  
**Description**: Lists available Spring Boot Actuator endpoints

#### Parameters
None required.

#### Response
```json
{
  "endpoints": [
    {
      "id": "health",
      "enabled": true,
      "exposed": true,
      "operations": ["GET"],
      "description": "Application health information"
    },
    {
      "id": "metrics",
      "enabled": true,
      "exposed": true,
      "operations": ["GET"],
      "description": "Application metrics"
    }
  ],
  "health": {
    "status": "UP",
    "components": {
      "db": {"status": "UP"},
      "diskSpace": {"status": "UP"}
    }
  }
}
```

---

## Execution Tools

### Spring Shell Tool (Tinker Equivalent)

**Name**: `spring-shell`  
**Description**: Executes code within the Spring application context

#### Parameters
- `expression` (string, required): SpEL expression to evaluate
- `timeout` (integer, optional): Execution timeout in seconds

#### Example
```json
{
  "expression": "@userService.findByEmail('john@example.com')"
}
```

#### Response
```json
{
  "expression": "@userService.findByEmail('john@example.com')",
  "result": {
    "id": 1,
    "email": "john@example.com",
    "name": "John Doe"
  },
  "executionTime": 45,
  "type": "com.example.User"
}
```

#### Security
- Configurable sandbox execution
- Restricted package access
- Expression validation
- Timeout protection

### Test Execution Tool

**Name**: `test-execution`  
**Description**: Runs Spring Boot tests and analyzes results

#### Parameters
- `testClass` (string, optional): Specific test class to run
- `testMethod` (string, optional): Specific test method
- `includeStackTrace` (boolean, optional): Include full stack traces

#### Response
```json
{
  "summary": {
    "total": 25,
    "passed": 23,
    "failed": 2,
    "skipped": 0,
    "duration": 15.4
  },
  "failures": [
    {
      "testClass": "UserServiceTest",
      "testMethod": "shouldCreateUser",
      "error": "AssertionError: Expected 1 but was 0",
      "stackTrace": "..."
    }
  ],
  "suggestions": [
    "Check user validation logic in UserService.createUser()",
    "Verify database transaction configuration"
  ]
}
```

---

## Documentation Tools

### Search Docs Tool

**Name**: `search-docs`  
**Description**: Performs semantic search across Spring Boot documentation

#### Parameters
- `query` (string, required): Search query
- `version` (string, optional): Spring Boot version (default: latest)
- `category` (string, optional): Documentation category
- `maxResults` (integer, optional): Maximum results (default: 10)

#### Example
```json
{
  "query": "how to configure database connection pool",
  "version": "3.2.0",
  "maxResults": 5
}
```

#### Response
```json
{
  "query": "how to configure database connection pool",
  "results": [
    {
      "title": "Configuring a DataSource",
      "url": "https://docs.spring.io/spring-boot/docs/3.2.0/reference/html/data.html#data.sql.datasource",
      "relevanceScore": 0.95,
      "excerpt": "Spring Boot provides extensive configuration options for DataSource...",
      "codeExamples": [
        "spring.datasource.hikari.maximum-pool-size=20"
      ]
    }
  ]
}
```

### Documentation Management Tool

**Name**: `documentation-management`  
**Description**: Manages documentation sources and provides statistics

#### Parameters
- `operation` (string, required): Operation to perform (status, update, stats)
- `source` (string, optional): Specific documentation source
- `includeDetails` (boolean, optional): Include detailed information

#### Operations

##### Status
```json
{
  "operation": "status",
  "includeDetails": true
}
```

##### Update
```json
{
  "operation": "update",
  "source": "spring-boot"
}
```

##### Statistics
```json
{
  "operation": "stats"
}
```

#### Response (Stats)
```json
{
  "sources": [
    {
      "name": "spring-boot",
      "version": "3.2.0",
      "documents": 1250,
      "lastUpdated": "2024-01-15T08:00:00Z"
    }
  ],
  "totalDocuments": 5420,
  "embeddingsGenerated": 5420,
  "searchIndexSize": "45.2 MB"
}
```

---

## Tool Configuration

### Global Configuration

```yaml
spring-boost:
  mcp:
    tools:
      enabled: true
      timeout: 30s
      retries: 3
      
      # Tool-specific settings
      database-access: true
      code-execution: false
      file-access: false
      
      # Security settings
      sandbox:
        enabled: true
        allowed-packages: ["com.example"]
        restricted-operations: ["file.write", "system.exec"]
```

### Per-Tool Configuration

```yaml
spring-boost:
  tools:
    database-query:
      max-rows: 100
      timeout: 10s
      allowed-schemas: ["public", "app"]
      
    spring-shell:
      enabled: false  # Disable for production
      timeout: 5s
      max-memory: "64MB"
      
    search-docs:
      cache-size: 1000
      max-results: 20
```

## Error Handling

All tools follow consistent error handling patterns:

```json
{
  "error": {
    "code": "TOOL_EXECUTION_ERROR",
    "message": "Failed to execute database query",
    "details": {
      "tool": "database-query",
      "cause": "SQL syntax error",
      "sql": "SELCT * FROM users"
    },
    "timestamp": "2024-01-15T10:45:30.123Z"
  }
}
```

Common error codes:
- `TOOL_EXECUTION_ERROR`: General execution failure
- `INVALID_PARAMETERS`: Invalid or missing parameters
- `SECURITY_VIOLATION`: Security policy violation
- `TIMEOUT_ERROR`: Operation timeout
- `PERMISSION_DENIED`: Insufficient permissions

## Best Practices

### For AI Clients
1. **Parameter Validation**: Always validate parameters before calling tools
2. **Error Handling**: Implement proper error handling for tool failures
3. **Rate Limiting**: Respect tool execution limits
4. **Context Awareness**: Use tool results to inform subsequent operations

### For Developers
1. **Security**: Always enable sandbox mode in production
2. **Monitoring**: Monitor tool usage and performance
3. **Configuration**: Tune timeouts and limits based on your needs
4. **Logging**: Enable debug logging for troubleshooting

### Performance Tips
1. **Caching**: Enable result caching for expensive operations
2. **Batching**: Use batch operations where available
3. **Filtering**: Use parameters to limit result sets
4. **Indexing**: Ensure proper database indexing for query tools
