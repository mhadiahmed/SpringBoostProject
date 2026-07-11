# 🚀 Spring Boost

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mhadiahmed/spring-boost.svg)](https://central.sonatype.com/artifact/io.github.mhadiahmed/spring-boost)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build Status](https://img.shields.io/github/actions/workflow/status/mhadiahmed/SpringBoostProject/ci.yml?branch=main)](https://github.com/mhadiahmed/SpringBoostProject/actions)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)

> Published on Maven Central as `io.github.mhadiahmed:spring-boost` (current: **v0.2.0**). Not yet on Docker Hub/GHCR — download the jar from [GitHub Releases](https://github.com/mhadiahmed/SpringBoostProject/releases), pull from Maven Central (see below), or build from source. See [docs/usage.md](docs/usage.md) for a full walkthrough.

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

> ⚠️ The GitHub Releases page currently only has **v0.1.0**, which has known
> bugs fixed in v0.2.0 (see [KNOWN_ISSUES.md](KNOWN_ISSUES.md)) — don't use
> it. Until a v0.2.0 GitHub Release exists, build from source (Option 2) or
> pull straight from Maven Central (Option 3, for embedding).

### Option 1: Download the release jar

```bash
curl -L -o spring-boost.jar \
  https://github.com/mhadiahmed/SpringBoostProject/releases/download/v0.1.0/spring-boost-0.1.0.jar
```

### Option 2: Build from source (requires Java 17+ and Maven)

```bash
git clone https://github.com/mhadiahmed/SpringBoostProject.git
cd SpringBoostProject
mvn clean package -DskipTests
```

This produces `target/spring-boost-0.2.0-exec.jar` — the executable jar (the
classifier-less `target/spring-boost-0.2.0.jar` is a plain library jar with no
`Main-Class`; see Option 3 below).

### Then, from your Spring Boot project's root

Publish the AI guidelines/skills and get your editor's registration command:

```bash
java -jar /path/to/spring-boost-0.2.0-exec.jar install
```

`install` prints the exact registration command for your editor (Claude Code, Cursor, Codex, Gemini CLI) — see [AI Client Setup](#-ai-client-setup) below.

### Option 3: Maven/Gradle dependency (embed in your own app)

Adding spring-boost as a dependency auto-configures the MCP tool registry and
`/mcp` WebSocket endpoint inside your own app's Spring context (`IN_PROCESS`
mode) — so `application-info`, `database-schema`, etc. see your app's real
beans/DataSource instead of an honest "standalone, can't see your app" error.
Disable it with `spring-boost.mcp.enabled=false` if you don't want this.

```xml
<dependency>
    <groupId>io.github.mhadiahmed</groupId>
    <artifactId>spring-boost</artifactId>
    <version>0.2.0</version>
</dependency>
```

```gradle
implementation 'io.github.mhadiahmed:spring-boost:0.2.0'
```

> Note: this only works from v0.2.0 onward. v0.1.0 was published as Spring
> Boot's repackaged executable jar (classes under `BOOT-INF/classes/`), which
> a normal classloader can't resolve as a library dependency at all — v0.2.0
> splits the executable jar out to its own `exec` classifier so the
> classifier-less artifact is a normal, classes-at-root library jar.

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
    embeddings-provider: simple  # only real option — 'openai'/'local' are unimplemented stubs, see docs/usage.md
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

Under the hood, `mcp` connects to a small shared background daemon (auto-started
on first use, one per machine) that keeps the Spring context warm instead of
paying full JVM+Spring boot on every session. The **first** connection after a
reboot (or after the daemon hasn't run in a while) takes a few seconds while
it starts; every connection after that is near-instant. This is transparent —
nothing to configure — but explains why the very first `claude mcp get` (or
equivalent) can be slower than the rest.

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

No Maven/Gradle wrapper is committed to this repo yet, so use a system-installed Maven or Gradle:

```bash
# Clone the repository
git clone https://github.com/mhadiahmed/SpringBoostProject.git
cd SpringBoostProject

# Build with Maven
mvn clean package

# Or build with Gradle (if installed)
gradle build

# Run tests
mvn test

# Run the long-running WebSocket server (no args = default server mode)
java -jar target/spring-boost-0.2.0-exec.jar
```

### Running Tests

```bash
# Fast tests only (recommended for development)
mvn test

# Include integration tests
mvn test -Dtest="**/*IntegrationTest"

# All tests including performance benchmarks
mvn verify
```

## 🐳 Docker Development

```bash
# Build image (verified this pass — arm64/Apple Silicon compatible)
docker build -t springboostproject .
```

`docker-compose.yml` also exists for running the long-lived server with a
real Postgres, but isn't independently re-verified this pass — check it
matches your setup (ports, `SPRING_DATASOURCE_URL`, etc.) before relying on it.

## 📖 Documentation

- **[Usage Guide](docs/usage.md)** - Full walkthrough: install, register, use, configure, troubleshoot
- **[Installation Guide](docs/installation.md)** - Build-from-source and registration details
- **[Tool Reference](docs/tools.md)** - Complete tool documentation
- **[Known Issues](KNOWN_ISSUES.md)** - What's genuinely fixed vs. still open, with how each was verified

## 🔧 Development Roadmap

- [x] **Phase 1**: Core infrastructure and MCP server
- [x] **Phase 2**: Database and web tools
- [x] **Phase 3**: Advanced tools and code execution
- [x] **Phase 4**: AI guidelines system
- [x] **Phase 5**: Documentation API with semantic search
- [x] **Phase 6**: Integration testing and optimization
- [x] **Phase 7**: Distribution and packaging
- [x] **Phase 8**: Laravel Boost architectural parity — stdio transport, core/extension tool split, Guidelines vs. Skills, install/update CLI
- [x] **Phase 9**: Real MCP connection reliability (root-caused a JSON-RPC envelope bug, not just speed) and working app embedding (`IN_PROCESS` mode) — see [KNOWN_ISSUES.md](KNOWN_ISSUES.md)
- [ ] **Next up**: CLI boot-noise cleanup, smaller jar (currently ~107MB), daemon lifecycle commands (`status`/`stop`), Windows verification — see [Recommendations](docs/usage.md#recommended-next-steps) in the usage guide

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