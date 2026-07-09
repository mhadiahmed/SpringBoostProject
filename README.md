# 🚀 Spring Boost

[![Maven Central](https://img.shields.io/maven-central/v/com.springboost/spring-boost.svg)](https://search.maven.org/artifact/com.springboost/spring-boost)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://img.shields.io/github/actions/workflow/status/mhadiahmed/SpringBoostProject/ci.yml?branch=main)](https://github.com/mhadiahmed/SpringBoostProject/actions)
[![Docker](https://img.shields.io/docker/pulls/mhadiahmed/springboostproject.svg)](https://hub.docker.com/r/mhadiahmed/springboostproject)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)

**The Laravel Boost equivalent for Spring Boot developers** - An MCP (Model Context Protocol) server that accelerates AI-assisted Spring Boot development by providing essential context and specialized tools that AI needs to generate high-quality, framework-specific code.

## ✨ Features

- **🔧 9 Core MCP Tools** - Exact tool-for-tool parity with Laravel Boost, plus optional Spring-only extensions (off by default)
- **🧩 Guidelines & Skills** - Always-loaded AI guidelines for broad conventions, on-demand Agent Skills for task-specific patterns — same split Laravel Boost uses
- **🔌 stdio MCP transport** - Registers with Claude Code / Cursor / Codex / Gemini CLI the same way Boost does (`... mcp`), no persistent server process required
- **📦 Install/Update CLI** - `spring-boost install` / `spring-boost update` publish guidelines & skills into your project, mirroring `artisan boost:install` / `boost:update`
- **📚 Documentation Search** - Local semantic search over the bundled Spring guideline corpus (not a hosted service — see [Documentation Search](#-documentation-search) below)
- **🗄️ Database Integration** - Direct database interaction and schema inspection
- **🎯 Application Context** - Deep Spring application inspection capabilities
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
docker run -p 8080:8080 -p 28080:28080 mhadiahmed/springboostproject:latest
```

## 🛠️ Available MCP Tools

### Core (enabled by default — exact Laravel Boost parity)

| Tool | Notes |
|------|-------|
| **Application Info** | Read Spring Boot version, profiles, beans, and configuration |
| **Browser Logs** | Read logs and errors from the browser |
| **Database Connections** | Inspect available DataSource configurations, including the default connection |
| **Database Query** | Execute a SELECT query against the database |
| **Database Schema** | Read the database schema and JPA entity mappings |
| **Get Absolute URL** | Convert relative path URIs to absolute so agents generate valid URLs |
| **Last Error** | Read the last error from the application's log files |
| **Read Log Entries** | Read the last N log entries |
| **Search Docs** | Query the local, guideline-backed documentation search |

### Extensions (opt-in, no Boost equivalent)

Disabled by default so the out-of-the-box tool set matches Boost's 9 tools
exactly. Enable with `spring-boost.mcp.tools.extensions-enabled: true`.

| Tool | Description |
|------|-------------|
| **Spring Shell** | Execute SpEL expressions within the Spring application context (Tinker-style) |
| **List Endpoints** | Inspect REST endpoints and their mappings |
| **List Actuator Endpoints** | Inspect available Actuator endpoints |
| **Test Execution** | Run and analyze test results |
| **Documentation Management** | Manage documentation sources and stats |

## 🧩 Guidelines vs. Skills

Same split Laravel Boost uses:

| | Guidelines (`.ai/guidelines/`) | Skills (`.ai/skills/*/SKILL.md`) |
|---|---|---|
| **Loaded** | Upfront, always present | On-demand, when relevant to the task |
| **Scope** | Broad, foundational conventions per framework/version | Focused, task-specific patterns |
| **Examples** | `core/spring-security.md`, `spring-boot/3.x/core.md` | `spring-data-jpa-development`, `testcontainers-testing`, `mcp-development` |

Run `spring-boost install` to publish both into your project's `.ai/` directory.

## 📚 Documentation Search

The `Search Docs` tool searches the AI guidelines bundled with Spring Boost —
a local corpus, not a hosted service. Laravel Boost's `Search Docs` queries a
Laravel-maintained hosted API covering 17,000+ real documentation entries
across the Laravel ecosystem; there's no Spring equivalent of that
infrastructure, so this project can't claim byte-for-byte parity there. The
local guideline search is the honest substitute.

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
    tools:
      database-access: true
      code-execution: false   # Enable with caution
      extensions-enabled: false # Opt in to the 5 Spring-only tools beyond Boost parity
  documentation:
    enabled: true
    embeddings-provider: openai  # or 'mock' for development
    cache-size: 1000
  security:
    sandbox:
      enabled: true
      allowed-packages: ["com.example"]
```

## 📦 Install & Update

From your Spring Boot project's root:

```bash
java -jar spring-boost.jar install   # publish .ai/guidelines + .ai/skills, print editor setup
java -jar spring-boost.jar update    # refresh already-published guidelines/skills
java -jar spring-boost.jar update --discover  # also publish any newly-added ones
```

`install` skips files you've already customized unless you pass `--force`.

## 🔌 AI Client Setup

Spring Boost runs as a **stdio** MCP server — one process per editor session,
spawned on demand — exactly like Laravel Boost's `php artisan boost:mcp`. No
persistent server or open port required.

### Claude Code

```bash
claude mcp add -s local -t stdio spring-boost -- java -jar spring-boost.jar mcp
```

### Codex

```bash
codex mcp add spring-boost -- java -jar spring-boost.jar mcp
```

### Gemini CLI

```bash
gemini mcp add -s project -t stdio spring-boost java -jar spring-boost.jar mcp
```

### Cursor / manual registration

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

## 🏗️ Development

### Prerequisites
- Java 17 or higher
- Maven 3.6+ or Gradle 7+
- Git

### Building from Source

```bash
# Clone the repository
git clone https://github.com/mhadiahmed/SpringBoostProject.git
cd SpringBoostProject

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
docker build -t springboostproject .

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
- [x] **Phase 8**: Laravel Boost architectural parity — stdio transport, core/extension tool split, Guidelines vs. Skills, install/update CLI

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

- **GitHub**: [https://github.com/mhadiahmed/SpringBoostProject](https://github.com/mhadiahmed/SpringBoostProject)
- **Issues**: [https://github.com/mhadiahmed/SpringBoostProject/issues](https://github.com/mhadiahmed/SpringBoostProject/issues)
- **Discussions**: [https://github.com/mhadiahmed/SpringBoostProject/discussions](https://github.com/mhadiahmed/SpringBoostProject/discussions)
- **Documentation**: Available in the [docs/](https://github.com/mhadiahmed/SpringBoostProject/tree/main/docs) directory

## 🌟 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=mhadiahmed/SpringBoostProject&type=Date)](https://star-history.com/#mhadiahmed/SpringBoostProject&Date)

---

**Made with ❤️ for the Spring Boot community**