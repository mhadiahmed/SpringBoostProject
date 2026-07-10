# Installation Guide

This guide covers all the different ways to install and run Spring Boost in your development environment.

## Prerequisites

- **Java 17 or higher** - Spring Boot 3.x requirement
- **Maven 3.6+** or **Gradle 7+** - Build tools (no wrapper is committed yet, so a system install of one of these is required)
- **Git** - For cloning the repository

> Spring Boost is published on Maven Central as `io.github.mhadiahmed:spring-boost` (not yet on Docker Hub). Download the pre-built jar from [GitHub Releases](https://github.com/mhadiahmed/SpringBoostProject/releases), add it as a dependency, or build from source below.

## Building from Source

#### Clone the Repository

```bash
git clone https://github.com/mhadiahmed/SpringBoostProject.git
cd SpringBoostProject
```

#### Build with Maven

```bash
# Clean and build
mvn clean package

# Skip tests for faster build
mvn clean package -DskipTests
```

#### Build with Gradle (if installed)

```bash
gradle clean build
gradle clean build -x test
```

This produces `target/spring-boost-0.1.0.jar` — a self-contained executable jar.

## Register the MCP Server (stdio)

Spring Boost registers as a stdio-transport MCP server — one process per
editor session, spawned on demand, no port to open. From your Spring Boot
project's root:

```bash
java -jar /path/to/spring-boost-0.1.0.jar install
```

`install` publishes the AI guidelines/skills into your project's `.ai/`
directory and prints the exact registration command for your editor. For
Claude Code specifically:

```bash
claude mcp add -s local -t stdio spring-boost -- java -jar /path/to/spring-boost-0.1.0.jar mcp
```

See [AI Client Setup](../README.md#-ai-client-setup) in the README for Codex, Gemini CLI, and Cursor equivalents.

## Running the Long-Running Server (optional)

Spring Boost can also run as an always-on WebSocket server (e.g. inside
Docker or as a dependency in your own app), separate from the stdio
integration above:

```bash
# Run directly
java -jar target/spring-boost-0.1.0.jar

# Or build and run in Docker
docker build -t spring-boost .
docker run -p 8080:8080 -p 28080:28080 spring-boost
```

## Configuration

### Basic Configuration

Create or update `application.yml`:

```yaml
spring-boost:
  mcp:
    enabled: true
    port: 28080
    host: localhost
    protocol: websocket
    tools:
      enabled: true
      database-access: true
      code-execution: false
  documentation:
    enabled: true
    embeddings-provider: mock  # or 'openai' for production
    cache-size: 1000
    search-timeout: 30s
    auto-update: true
  security:
    sandbox:
      enabled: true
      allowed-packages: ["com.example", "org.springframework"]
      restricted-operations: ["file.write", "system.exec"]
  logging:
    level: INFO
    file: "logs/spring-boost.log"
```

### Environment-Specific Configuration

#### Development (application-dev.yml)
```yaml
spring-boost:
  mcp:
    tools:
      code-execution: true  # Enable for development
  documentation:
    embeddings-provider: mock
  security:
    sandbox:
      enabled: false  # Relaxed for development
```

#### Production (application-prod.yml)
```yaml
spring-boost:
  mcp:
    host: 0.0.0.0  # Allow external connections
    tools:
      code-execution: false  # Disabled for security
  documentation:
    embeddings-provider: openai
    cache-size: 5000
  security:
    sandbox:
      enabled: true
      restricted-operations: ["file.write", "system.exec", "network.external"]
```

### Database Configuration

For database tools to work, configure your datasource:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
    password: ""
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
```

### Environment Variables

You can also configure using environment variables:

```bash
export SPRING_BOOST_MCP_ENABLED=true
export SPRING_BOOST_MCP_PORT=28080
export SPRING_BOOST_DOCUMENTATION_ENABLED=true
export SPRING_PROFILES_ACTIVE=development
```

## Verification

### Check Installation

```bash
# List available tools (core vs extensions)
java -jar spring-boost.jar --list-tools

# Validate configuration
java -jar spring-boost.jar --validate-config

# Confirm the stdio MCP transport starts (Ctrl+C to exit)
java -jar spring-boost.jar mcp
```

If you're also running the full application (Docker/Maven/Gradle modes),
`/actuator/health` and the WebSocket endpoint remain available for that
always-on mode — but editor integration should use the `mcp` stdio
subcommand above, not the WebSocket transport.

## Troubleshooting

### Common Issues

#### Port Already in Use
```bash
# Check what's using the port
lsof -i :28080

# Kill the process or change the port
spring-boost:
  mcp:
    port: 28081
```

#### Java Version Issues
```bash
# Check Java version
java -version

# Should be 17 or higher
sudo apt update && sudo apt install openjdk-17-jdk  # Ubuntu/Debian
brew install openjdk@17  # macOS
```

#### Permission Denied
```bash
# Make sure the JAR is executable
chmod +x spring-boost.jar

# Or run directly with java
java -jar spring-boost.jar
```

#### WebSocket Connection Failed
- Check firewall settings
- Verify the port is not blocked
- Ensure Spring Boost is running
- Check application logs for errors

### Getting Help

If you encounter issues:

1. **Check the logs**: `logs/spring-boost.log`
2. **Validate configuration**: `spring-boost --validate-config`
3. **Check system requirements**: Java 17+, available ports
4. **Search existing issues**: [GitHub Issues](https://github.com/mhadiahmed/SpringBoostProject/issues)
5. **Ask for help**: [GitHub Discussions](https://github.com/mhadiahmed/SpringBoostProject/discussions)

## Next Steps

After installation:

1. **Configure your AI client** - See [AI Client Setup](ai-clients.md)
2. **Explore available tools** - See [Tool Reference](tools.md)
3. **Configure for your project** - See [Configuration Guide](configuration.md)
4. **Join the community** - [GitHub Discussions](https://github.com/mhadiahmed/SpringBoostProject/discussions)
