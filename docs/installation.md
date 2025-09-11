# Installation Guide

This guide covers all the different ways to install and run Spring Boost in your development environment.

## Prerequisites

- **Java 17 or higher** - Spring Boot 3.x requirement
- **Maven 3.6+** or **Gradle 7+** - Build tools
- **Git** - For cloning the repository
- **curl** or **wget** - For script downloads

## Installation Methods

### 1. One-Line Installation (Recommended)

The easiest way to get started:

```bash
curl -sSL https://install.springboost.com | bash
```

Or with options:
```bash
curl -sSL https://install.springboost.com | bash -s -- --method jar --version latest
```

### 2. Manual JAR Installation

#### Download and Install

```bash
# Create installation directory
mkdir -p ~/.spring-boost

# Download latest release (replace VERSION with actual version)
curl -L -o ~/.spring-boost/spring-boost.jar \
  https://github.com/springboost/spring-boost/releases/download/v1.0.0/spring-boost-1.0.0.jar

# Create executable wrapper
cat > ~/.spring-boost/spring-boost << 'EOF'
#!/bin/bash
SPRING_BOOST_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -jar "$SPRING_BOOST_HOME/spring-boost.jar" "$@"
EOF

chmod +x ~/.spring-boost/spring-boost

# Add to PATH
echo 'export PATH="$HOME/.spring-boost:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

#### Verify Installation

```bash
spring-boost --version
spring-boost --list-tools
```

### 3. Maven Dependency

Add Spring Boost as a dependency to your existing Spring Boot project:

#### Add to pom.xml

```xml
<dependencies>
    <!-- Your existing dependencies -->
    
    <dependency>
        <groupId>com.springboost</groupId>
        <artifactId>spring-boost</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

#### Configuration

Add to your `application.yml`:

```yaml
spring-boost:
  mcp:
    enabled: true
    port: 28080
    host: localhost
```

#### Run Your Application

```bash
mvn spring-boot:run
```

The MCP server will be available at `ws://localhost:28080`

### 4. Gradle Dependency

Add Spring Boost to your Gradle project:

#### Add to build.gradle

```gradle
dependencies {
    implementation 'com.springboost:spring-boost:1.0.0'
    // Your other dependencies
}
```

#### Configuration

Same as Maven - add configuration to `application.yml`.

#### Run Your Application

```bash
./gradlew bootRun
```

### 5. Docker Installation

#### Quick Start with Docker

```bash
# Run with default settings
docker run -p 8080:8080 -p 28080:28080 springboost/spring-boost:latest

# Run with custom configuration
docker run -p 8080:8080 -p 28080:28080 \
  -e SPRING_PROFILES_ACTIVE=production \
  -e SPRING_BOOST_MCP_PORT=28080 \
  -v $(pwd)/config:/app/config \
  springboost/spring-boost:latest
```

#### Using Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  spring-boost:
    image: springboost/spring-boost:latest
    ports:
      - "8080:8080"
      - "28080:28080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - SPRING_BOOST_MCP_ENABLED=true
    volumes:
      - spring-boost-data:/app/data
    restart: unless-stopped

volumes:
  spring-boost-data:
```

Run with:
```bash
docker-compose up -d
```

#### Custom Docker Build

If you want to build from source:

```bash
git clone https://github.com/springboost/spring-boost.git
cd spring-boost
docker build -t my-spring-boost .
docker run -p 8080:8080 -p 28080:28080 my-spring-boost
```

### 6. Building from Source

#### Clone the Repository

```bash
git clone https://github.com/springboost/spring-boost.git
cd spring-boost
```

#### Build with Maven

```bash
# Clean and build
./mvnw clean package

# Skip tests for faster build
./mvnw clean package -DskipTests

# Run the application
./mvnw spring-boot:run
```

#### Build with Gradle

```bash
# Clean and build
./gradlew clean build

# Skip tests for faster build
./gradlew clean build -x test

# Run the application
./gradlew bootRun
```

#### Development Mode

For development with auto-reload:

```bash
# Maven
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=true"

# Gradle
./gradlew bootRun --continuous
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
# Check if Spring Boost is accessible
curl http://localhost:8080/actuator/health

# List available tools
curl http://localhost:8080/actuator/springboost/tools

# Test MCP connection
wscat -c ws://localhost:28080
```

### Test MCP Tools

```bash
# Using spring-boost CLI
spring-boost --list-tools
spring-boost --validate-config

# Test a specific tool
spring-boost --tool application-info
```

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
4. **Search existing issues**: [GitHub Issues](https://github.com/springboost/spring-boost/issues)
5. **Ask for help**: [GitHub Discussions](https://github.com/springboost/spring-boost/discussions)

## Next Steps

After installation:

1. **Configure your AI client** - See [AI Client Setup](ai-clients.md)
2. **Explore available tools** - See [Tool Reference](tools.md)
3. **Configure for your project** - See [Configuration Guide](configuration.md)
4. **Join the community** - [GitHub Discussions](https://github.com/springboost/spring-boost/discussions)
