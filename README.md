# 🚀 Spring Boost

[![Maven Central](https://img.shields.io/maven-central/v/com.springboost/spring-boost.svg)](https://search.maven.org/artifact/com.springboost/spring-boost)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://img.shields.io/github/actions/workflow/status/springboost/spring-boost/ci.yml?branch=main)](https://github.com/springboost/spring-boost/actions)
[![Docker](https://img.shields.io/docker/pulls/springboost/spring-boost.svg)](https://hub.docker.com/r/springboost/spring-boost)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)

**The Laravel Boost equivalent for Spring Boot developers** - An MCP (Model Context Protocol) server that accelerates AI-assisted Spring Boot development by providing essential context and specialized tools that AI needs to generate high-quality, framework-specific code.

## ✨ Features

- **🔧 15+ MCP Tools** - Specialized tools for Spring Boot development
- **🧠 AI Guidelines** - Composable AI guidelines for the Spring Boot ecosystem
- **📚 Documentation API** - Semantic search across Spring Boot documentation
- **🗄️ Database Integration** - Direct database interaction and schema inspection
- **🎯 Application Context** - Deep Spring application inspection capabilities
- **⚡ Performance Optimized** - Lightning-fast responses with intelligent caching
- **🐳 Docker Ready** - Containerized deployment support
- **🔒 Security First** - Sandboxed execution with configurable permissions

## 🚀 Quick Start

### Option 1: One-Line Install (Recommended)

```bash
curl -sSL https://install.springboost.com | bash
```

### Option 2: Maven Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.springboost</groupId>
    <artifactId>spring-boost</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Option 3: Gradle Dependency

Add to your `build.gradle`:

```gradle
implementation 'com.springboost:spring-boost:1.0.0'
```

### Option 4: Docker

```bash
docker run -p 8080:8080 -p 28080:28080 springboost/spring-boost:latest
```

## 🛠️ Available MCP Tools

| Tool | Description | Category |
|------|-------------|----------|
| **Application Info** | Read Spring Boot version, profiles, beans, and configuration | Application |
| **Database Connections** | Inspect DataSource configurations and connection pools | Database |
| **Database Schema** | Read database schema and JPA entity mappings | Database |
| **Database Query** | Execute SELECT queries against the database | Database |
| **List Endpoints** | Inspect REST endpoints and their mappings | Web |
| **Get Absolute URL** | Convert relative paths to absolute URLs | Web |
| **Last Error** | Read the last error from application logs | Logging |
| **Read Log Entries** | Read last N log entries with filtering | Logging |
| **Browser Logs** | Read browser console logs and errors | Monitoring |
| **Spring Shell** | Execute code within Spring application context (Tinker equivalent) | Execution |
| **Search Docs** | Query Spring documentation with semantic search | Documentation |
| **List Actuator Endpoints** | Inspect available Actuator endpoints | Monitoring |
| **Test Execution** | Run and analyze test results | Testing |
| **Documentation Management** | Manage documentation sources and stats | Documentation |

## 🎯 Use Cases

### 🤖 AI-Assisted Development
Connect Spring Boost to your favorite AI coding assistant (Cursor, Claude, etc.) to get:
- **Contextual Code Generation** - AI understands your Spring Boot application structure
- **Database-Aware Suggestions** - AI knows your schema and can generate proper queries
- **Configuration Help** - AI can read and suggest configuration improvements
- **Debugging Assistance** - AI can analyze logs and suggest fixes

### 🔍 Application Inspection
- Understand complex Spring Boot applications quickly
- Inspect bean dependencies and configurations
- Analyze database schemas and relationships
- Monitor application health and performance

### 🧪 Development Productivity
- Execute code snippets in Spring context (like Laravel Tinker)
- Test API endpoints directly
- Query databases safely
- Access comprehensive documentation instantly

## 📋 Configuration

Create `application.yml` in your Spring Boot project:

```yaml
spring-boost:
  mcp:
    enabled: true
    port: 28080
    host: localhost
    tools:
      database-access: true
      code-execution: false  # Enable with caution
  documentation:
    enabled: true
    embeddings-provider: openai  # or 'mock' for development
    cache-size: 1000
  security:
    sandbox:
      enabled: true
      allowed-packages: ["com.example"]
```

## 🔌 AI Client Setup

### Cursor IDE

Add to your `.cursorrules` or workspace settings:

```json
{
  "mcp": {
    "servers": {
      "spring-boost": {
        "command": "java",
        "args": ["-jar", "spring-boost.jar"],
        "transport": "websocket",
        "url": "ws://localhost:28080"
      }
    }
  }
}
```

### Claude Desktop

Add to your MCP configuration:

```json
{
  "mcpServers": {
    "spring-boost": {
      "command": "spring-boost",
      "transport": {
        "type": "websocket",
        "url": "ws://localhost:28080"
      }
    }
  }
}
```

## 🏗️ Development

### Prerequisites
- Java 17 or higher
- Maven 3.6+ or Gradle 7+
- Git

### Building from Source

```bash
# Clone the repository
git clone https://github.com/springboost/spring-boost.git
cd spring-boost

# Build with Maven
./mvnw clean package

# Or build with Gradle
./gradlew build

# Run tests
./mvnw test

# Run the application
./mvnw spring-boot:run
```

### Running Tests

```bash
# Fast tests only (recommended for development)
./mvnw test

# Include integration tests
./mvnw test -Dtest="**/*IntegrationTest"

# All tests including performance benchmarks
./mvnw verify
```

## 🐳 Docker Development

```bash
# Build image
docker build -t spring-boost .

# Run with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f spring-boost
```

## 📖 Documentation

- **[Installation Guide](docs/installation.md)** - Detailed installation instructions
- **[Tool Reference](docs/tools.md)** - Complete tool documentation
- **[Configuration Guide](docs/configuration.md)** - Configuration options
- **[AI Guidelines](docs/guidelines.md)** - AI-specific guidelines
- **[Troubleshooting](docs/troubleshooting.md)** - Common issues and solutions

## 🔧 Development Roadmap

- [x] **Phase 1**: Core infrastructure and MCP server
- [x] **Phase 2**: Database and web tools
- [x] **Phase 3**: Advanced tools and code execution
- [x] **Phase 4**: AI guidelines system
- [x] **Phase 5**: Documentation API with semantic search
- [x] **Phase 6**: Integration testing and optimization
- [x] **Phase 7**: Distribution and packaging

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by [Laravel Boost](https://github.com/laravel/boost) - bringing similar productivity to Spring Boot
- Built on the [Model Context Protocol](https://modelcontextprotocol.io/) specification
- Spring Boot team for the amazing framework

## 🔗 Links

- **Website**: [https://springboost.com](https://springboost.com)
- **Documentation**: [https://docs.springboost.com](https://docs.springboost.com)
- **GitHub**: [https://github.com/springboost/spring-boost](https://github.com/springboost/spring-boost)
- **Issues**: [https://github.com/springboost/spring-boost/issues](https://github.com/springboost/spring-boost/issues)
- **Discussions**: [https://github.com/springboost/spring-boost/discussions](https://github.com/springboost/spring-boost/discussions)

## 🌟 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=springboost/spring-boost&type=Date)](https://star-history.com/#springboost/spring-boost&Date)

---

**Made with ❤️ for the Spring Boot community**