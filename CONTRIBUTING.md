# Contributing to Spring Boost

Thank you for your interest in contributing to Spring Boost! We welcome contributions from the community and are excited to see what you build.

## 🤝 How to Contribute

### 1. Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/your-username/spring-boost.git
   cd spring-boost
   ```
3. **Set up** the development environment:
   ```bash
   ./mvnw clean install
   ```

### 2. Development Setup

#### Prerequisites
- Java 17 or higher
- Maven 3.6+ or Gradle 7+
- Git
- Your favorite IDE (IntelliJ IDEA, Eclipse, VS Code)

#### IDE Configuration
- **IntelliJ IDEA**: Import as Maven project, enable annotation processing
- **Eclipse**: Import as existing Maven project
- **VS Code**: Use the Java Extension Pack

### 3. Making Changes

#### Branch Naming
- Feature branches: `feature/descriptive-name`
- Bug fixes: `bugfix/issue-description`
- Documentation: `docs/section-name`
- Examples: `feature/database-query-tool`, `bugfix/websocket-connection`

#### Code Style
We follow standard Java conventions with these specifics:

- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters max
- **Imports**: Group and organize automatically
- **Naming**: Use descriptive names, follow camelCase
- **Comments**: Use Javadoc for public APIs

#### Code Quality Checklist
- [ ] Code follows the project's style guidelines
- [ ] All tests pass locally
- [ ] New code has appropriate test coverage
- [ ] Documentation is updated for new features
- [ ] No unused imports or variables
- [ ] Proper error handling and logging

### 4. Types of Contributions

#### 🐛 Bug Reports
Use the bug report template and include:
- Clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Environment details (Java version, OS, etc.)
- Log files if relevant

#### ✨ Feature Requests
Use the feature request template and include:
- Clear description of the proposed feature
- Use case and motivation
- Proposed implementation approach
- Any breaking changes

#### 🔧 Code Contributions

##### New MCP Tools
When adding a new MCP tool:

1. **Create the tool class** in `src/main/java/com/springboost/mcp/tools/impl/`
2. **Implement the interface**:
   ```java
   @Component
   public class YourNewTool implements McpTool {
       @Override
       public String getName() { return "your-tool-name"; }
       
       @Override
       public String getCategory() { return "category"; }
       
       @Override
       public String getDescription() { return "Tool description"; }
       
       @Override
       public Map<String, Object> getParameterSchema() {
           // Return JSON schema for parameters
       }
       
       @Override
       public Object execute(Map<String, Object> params) throws McpToolException {
           // Tool implementation
       }
   }
   ```
3. **Add tests** in `src/test/java/com/springboost/mcp/tools/impl/`
4. **Update documentation** including the tool in README.md

##### Documentation
- Update README.md for new features
- Add examples in `docs/examples/`
- Update tool reference in `docs/tools.md`
- Add configuration options to `docs/configuration.md`

### 5. Testing

#### Running Tests
```bash
# Fast tests (unit tests only)
./mvnw test

# Integration tests
./mvnw test -Dtest="**/*IntegrationTest"

# All tests
./mvnw verify
```

#### Test Coverage
- Aim for 80%+ test coverage for new code
- Write unit tests for all public methods
- Add integration tests for complex features
- Mock external dependencies appropriately

#### Test Categories
- **Unit Tests**: Fast, isolated tests (`*Test.java`)
- **Integration Tests**: Test component interactions (`*IntegrationTest.java`)
- **Performance Tests**: Benchmark tests (`*PerformanceBenchmarkTest.java`)

### 6. Documentation

#### README Updates
- Keep the tool list updated
- Add new configuration options
- Update installation instructions if needed

#### Code Documentation
- Use Javadoc for all public APIs
- Add inline comments for complex logic
- Include usage examples in docstrings

#### User Documentation
- Update `docs/` directory for user-facing changes
- Include screenshots for UI changes
- Add troubleshooting entries for common issues

### 7. Submitting Changes

#### Commit Messages
Follow the conventional commits format:
```
type(scope): description

[optional body]

[optional footer]
```

Examples:
```
feat(tools): add database query execution tool
fix(websocket): resolve connection timeout issue
docs(readme): update installation instructions
test(integration): add MCP server connectivity tests
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Formatting, missing semicolons, etc.
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `test`: Adding missing tests
- `chore`: Updating grunt tasks, etc.

#### Pull Request Process

1. **Create a pull request** with a clear title and description
2. **Fill out the PR template** completely
3. **Ensure CI passes** (tests, linting, etc.)
4. **Request review** from maintainers
5. **Address feedback** promptly and respectfully
6. **Squash commits** if requested before merging

#### PR Template
```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] No breaking changes (or documented)
```

### 8. Community Guidelines

#### Code of Conduct
- Be respectful and inclusive
- Welcome newcomers and help them get started
- Focus on constructive feedback
- Report unacceptable behavior to maintainers

#### Communication Channels
- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: General questions and community chat
- **Pull Requests**: Code review and technical discussions

### 9. Development Workflow

#### Typical Workflow
1. **Check issues** for something to work on
2. **Comment on the issue** to claim it
3. **Create a branch** from `main`
4. **Make your changes** with tests
5. **Test thoroughly** locally
6. **Commit with good messages**
7. **Push and create PR**
8. **Respond to review feedback**
9. **Celebrate** when merged! 🎉

#### Getting Help
- Check existing documentation first
- Search closed issues for similar problems
- Ask questions in GitHub Discussions
- Reach out to maintainers if stuck

### 10. Recognition

Contributors are recognized in:
- **README.md**: Major contributors listed
- **CHANGELOG.md**: All contributions noted in releases
- **GitHub**: Contributor graph and statistics
- **Documentation**: Credits for significant contributions

## 🚀 Quick Start for Contributors

```bash
# 1. Fork and clone
git clone https://github.com/your-username/spring-boost.git
cd spring-boost

# 2. Build and test
./mvnw clean install
./mvnw test

# 3. Create feature branch
git checkout -b feature/my-awesome-feature

# 4. Make changes and test
# ... your code changes ...
./mvnw test

# 5. Commit and push
git add .
git commit -m "feat: add my awesome feature"
git push origin feature/my-awesome-feature

# 6. Create pull request on GitHub
```

## 📞 Need Help?

- 🐛 **Bugs**: [Create an issue](https://github.com/springboost/spring-boost/issues/new?template=bug_report.md)
- 💡 **Features**: [Request a feature](https://github.com/springboost/spring-boost/issues/new?template=feature_request.md)
- ❓ **Questions**: [Start a discussion](https://github.com/springboost/spring-boost/discussions)
- 📧 **Private**: Email maintainers at dev@springboost.com

Thank you for contributing to Spring Boost! 🙏
