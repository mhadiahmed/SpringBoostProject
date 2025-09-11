# Changelog

All notable changes to Spring Boost will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Performance improvements for tool execution
- Enhanced error handling and logging

### Changed
- Updated Spring Boot to 3.2.1
- Improved documentation with more examples

### Fixed
- Minor bug fixes and stability improvements

## [1.0.0] - 2024-01-15

### Added
- 🎉 **Initial release of Spring Boost!**
- **15+ MCP Tools** for Spring Boot development
  - Application Info Tool - Read Spring Boot version, profiles, beans
  - Database Tools - Connections, schema, and query execution
  - Web Tools - Endpoint listing and URL management
  - Logging Tools - Error analysis and log reading
  - Monitoring Tools - Actuator endpoints and browser logs
  - Execution Tools - Spring Shell (Tinker equivalent) and test execution
  - Documentation Tools - Semantic search and management

### Core Features
- **MCP Server** - WebSocket-based Model Context Protocol server
- **AI Guidelines System** - Composable guidelines for Spring Boot ecosystem
- **Documentation API** - Semantic search across Spring Boot documentation
- **Database Integration** - Safe database querying and schema inspection
- **Application Context Tools** - Deep Spring application inspection
- **Security Sandbox** - Configurable execution environment
- **Performance Optimization** - Intelligent caching and fast responses

### Installation Methods
- **One-line install** - `curl -sSL https://install.springboost.com | bash`
- **Maven dependency** - Available on Maven Central
- **Gradle dependency** - Gradle plugin support
- **Docker support** - Multi-stage containerized deployment
- **Standalone JAR** - Self-contained executable

### Development Tools
- **Maven build system** with comprehensive plugin configuration
- **Gradle build alternative** with same feature parity
- **Docker Compose** for local development
- **Comprehensive testing** - Unit, integration, and performance tests
- **CI/CD pipeline** - GitHub Actions for automated testing and release

### Documentation
- **Complete installation guide** with multiple options
- **Comprehensive tool reference** with examples and use cases
- **Configuration documentation** for all settings
- **AI client setup guides** for popular editors
- **Contributing guide** for community development
- **Troubleshooting documentation** for common issues

### AI Integration
- **Cursor IDE support** - Direct MCP integration
- **Claude Desktop support** - Native MCP client
- **Generic WebSocket support** - For any MCP-compatible client
- **Context-aware responses** - Tools provide rich application context

### Security Features
- **Sandbox execution environment** for code execution tools
- **Configurable permissions** - Package and operation restrictions
- **Read-only database access** - Safe query execution only
- **Input validation** - All tool parameters validated
- **Audit logging** - Complete operation tracking

### Performance
- **Sub-500ms response times** for 95% of tool executions
- **Intelligent caching** - Documentation and query result caching
- **Parallel execution** - Concurrent tool processing
- **Memory optimization** - Efficient resource usage
- **Connection pooling** - Database and external service connections

### Monitoring
- **Health checks** - Built-in application health monitoring
- **Metrics collection** - Performance and usage metrics
- **Error tracking** - Comprehensive error logging and analysis
- **Resource monitoring** - Memory, CPU, and connection tracking

## [1.0.0-RC.2] - 2024-01-10

### Added
- Performance benchmark tests
- Enhanced error handling in MCP tools
- Docker multi-stage build optimization

### Changed
- Improved test performance with optimized configurations
- Updated dependencies to latest stable versions

### Fixed
- Fixed NullPointerException in DocumentationManagementTool
- Resolved WebSocket connection timeout issues
- Fixed Maven compiler configuration for annotation processing

## [1.0.0-RC.1] - 2024-01-05

### Added
- Beta release with core MCP tools
- Basic documentation and installation scripts
- Docker support

### Changed
- Refactored tool registry for better performance
- Improved configuration management

### Fixed
- Various stability improvements
- Test suite optimizations

## [1.0.0-beta] - 2024-01-01

### Added
- Initial beta release
- Core MCP server implementation
- Basic tool set
- Documentation framework

---

## Release Process

### Version Numbering
- **Major (X.0.0)**: Breaking changes, major new features
- **Minor (1.X.0)**: New features, backward compatible
- **Patch (1.0.X)**: Bug fixes, no new features

### Release Types
- **Stable Release**: Fully tested, production-ready
- **Release Candidate (RC)**: Feature-complete, final testing
- **Beta**: Feature-complete, broader testing needed
- **Alpha**: Early preview, major features complete

### How to Release

1. **Update version** in `pom.xml` and `gradle.properties`
2. **Update CHANGELOG.md** with release notes
3. **Create release tag**: `git tag -a v1.0.0 -m "Release 1.0.0"`
4. **Push tag**: `git push origin v1.0.0`
5. **GitHub Actions** will automatically:
   - Build and test the release
   - Deploy to Maven Central
   - Push Docker images
   - Create GitHub release
   - Update documentation
   - Notify community channels

### Breaking Changes Policy
- Breaking changes are introduced only in major versions
- Deprecation warnings are provided one minor version before removal
- Migration guides are provided for all breaking changes
- Backward compatibility is maintained within major versions

### Support Policy
- **Current major version**: Full support with new features and bug fixes
- **Previous major version**: Security fixes and critical bug fixes for 12 months
- **Older versions**: Community support only

---

For more information about releases, see our [Release Guide](docs/releasing.md).
