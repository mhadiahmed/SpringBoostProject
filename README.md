# Spring Boost

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Spring Boost accelerates AI-assisted development by providing essential context and structure that AI needs to generate high-quality, Spring Boot-specific code.

## 🚀 Features

- **MCP Server**: 15+ specialized tools for Spring Boot development
- **AI Guidelines**: Composable AI guidelines for Spring Boot ecosystem packages  
- **Documentation API**: Semantic search across Spring Boot documentation and knowledge base
- **Database Integration**: Direct database interaction tools
- **Application Context Tools**: Deep Spring application inspection capabilities

## 📦 Installation

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Spring Boot 3.x application

### Quick Start

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-org/spring-boost.git
   cd spring-boost
   ```

2. **Build the project:**
   ```bash
   mvn clean install
   ```

3. **Run the MCP server:**
   ```bash
   mvn spring-boot:run
   ```

4. **Connect your AI assistant:**
   - WebSocket endpoint: `ws://localhost:8080/mcp`
   - Protocol: Model Context Protocol (MCP)

## 🛠️ Available Tools

| Tool Name | Description | Category |
|-----------|-------------|----------|
| Application Info | Read Spring Boot version, profiles, beans, and configuration | Application |
| Database Connections | Inspect DataSource configurations and connection pools | Database |
| Database Schema | Read database schema and JPA entity mappings | Database |
| Database Query | Execute SELECT queries against the database | Database |
| List Endpoints | Inspect REST endpoints and their mappings | Web |
| Get Config | Get configuration values using property resolution | Configuration |
| Get Absolute URL | Convert relative paths to absolute URLs | Web |
| Last Error | Read the last error from application logs | Logging |
| Read Log Entries | Read last N log entries with filtering | Logging |
| Spring Shell | Execute code within Spring application context | Execution |
| Search Docs | Query Spring documentation with semantic search | Documentation |

## ⚙️ Configuration

Configure Spring Boost through `application.yml`:

```yaml
spring-boost:
  mcp:
    enabled: true
    port: 8080
    host: localhost
    tools:
      enabled: true
      database-access: true
      code-execution: false
  documentation:
    enabled: true
    embeddings-provider: local
    cache-size: 1000
  security:
    sandbox-enabled: true
    allowed-packages: ["com.example", "org.springframework"]
```

## 🎯 Usage Examples

### Basic Application Information
```bash
# List all available tools
java -jar spring-boost.jar --list-tools

# Validate configuration
java -jar spring-boost.jar --validate-config
```

### Connect with AI Assistant

1. **Cursor/Claude Code**: Add to your MCP settings:
   ```json
   {
     "mcpServers": {
       "spring-boost": {
         "command": "java",
         "args": ["-jar", "spring-boost.jar", "mcp"]
       }
     }
   }
   ```

2. **WebSocket Connection**: Connect directly to `ws://localhost:8080/mcp`

## 🏗️ Development

### Project Structure

```
spring-boost/
├── src/main/java/com/springboost/
│   ├── SpringBoostApplication.java
│   ├── config/
│   │   ├── SpringBoostProperties.java
│   │   └── WebSocketConfig.java
│   ├── mcp/
│   │   ├── McpServer.java
│   │   ├── protocol/
│   │   │   ├── McpMessage.java
│   │   │   └── McpError.java
│   │   └── tools/
│   │       ├── McpTool.java
│   │       ├── McpToolRegistry.java
│   │       └── impl/
│   │           └── ApplicationInfoTool.java
│   └── cli/
│       └── BoostCommand.java
├── src/main/resources/
│   ├── application.yml
│   └── banner.txt
├── pom.xml
└── README.md
```

### Building from Source

```bash
# Clone the repository
git clone https://github.com/your-org/spring-boost.git
cd spring-boost

# Build the project
mvn clean install

# Run tests
mvn test

# Package the application
mvn package
```

## 🔧 Development Roadmap

- [x] **Phase 1**: Core infrastructure and MCP server
- [ ] **Phase 2**: Database and web tools
- [ ] **Phase 3**: Advanced tools and code execution
- [ ] **Phase 4**: AI guidelines system
- [ ] **Phase 5**: Documentation API with semantic search
- [ ] **Phase 6**: Integration testing and optimization
- [ ] **Phase 7**: Distribution and packaging

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by [Laravel Boost](https://github.com/laravel/boost)
- Built with [Spring Boot](https://spring.io/projects/spring-boot)
- Model Context Protocol (MCP) specification

## 📞 Support

- 📧 Email: support@spring-boost.com
- 🐛 Issues: [GitHub Issues](https://github.com/your-org/spring-boost/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/your-org/spring-boost/discussions)

---

**Spring Boost** - Accelerating AI-assisted Spring Boot development 🚀
