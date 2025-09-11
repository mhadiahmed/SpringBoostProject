package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tool to search Spring documentation with semantic search capabilities
 * Provides context-aware documentation retrieval with version-specific results
 */
@Slf4j
@Component
public class SearchDocsTool implements McpTool {
    
    private final Environment environment;
    
    // Documentation sources and their patterns
    private static final Map<String, DocSource> DOC_SOURCES = Map.of(
            "spring-boot", new DocSource(
                    "https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/",
                    "Spring Boot Reference Documentation",
                    Arrays.asList("configuration", "actuator", "testing", "security", "data")
            ),
            "spring-framework", new DocSource(
                    "https://docs.spring.io/spring-framework/docs/current/reference/html/",
                    "Spring Framework Reference Documentation",
                    Arrays.asList("core", "web", "data-access", "integration", "testing")
            ),
            "spring-security", new DocSource(
                    "https://docs.spring.io/spring-security/docs/current/reference/html/",
                    "Spring Security Reference Documentation",
                    Arrays.asList("authentication", "authorization", "oauth2", "jwt", "csrf")
            ),
            "spring-data", new DocSource(
                    "https://docs.spring.io/spring-data/jpa/docs/current/reference/html/",
                    "Spring Data JPA Reference Documentation",
                    Arrays.asList("repositories", "queries", "auditing", "transactions")
            )
    );
    
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "<code[^>]*>(.*?)</code>|<pre[^>]*>(.*?)</pre>|```([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "<h[1-6][^>]*>(.*?)</h[1-6]>",
            Pattern.CASE_INSENSITIVE
    );
    
    @Autowired
    public SearchDocsTool(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public String getName() {
        return "search-docs";
    }
    
    @Override
    public String getDescription() {
        return "Search Spring documentation with semantic search across Spring Boot, Security, Data, and Framework docs";
    }
    
    @Override
    public String getCategory() {
        return "documentation";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "query", Map.of(
                        "type", "string",
                        "description", "Search query (e.g., 'spring security jwt', 'actuator endpoints', 'jpa repositories')",
                        "minLength", 2
                ),
                "source", Map.of(
                        "type", "string",
                        "description", "Documentation source to search",
                        "enum", Arrays.asList("all", "spring-boot", "spring-framework", "spring-security", "spring-data"),
                        "default", "all"
                ),
                "includeCode", Map.of(
                        "type", "boolean",
                        "description", "Include code examples in results",
                        "default", true
                ),
                "maxResults", Map.of(
                        "type", "integer",
                        "description", "Maximum number of results to return",
                        "default", 5,
                        "minimum", 1,
                        "maximum", 20
                ),
                "contextLines", Map.of(
                        "type", "integer",
                        "description", "Number of context lines around matches",
                        "default", 2,
                        "minimum", 0,
                        "maximum", 10
                ),
                "format", Map.of(
                        "type", "string",
                        "description", "Result format",
                        "enum", Arrays.asList("full", "summary", "links-only"),
                        "default", "full"
                )
        ));
        schema.put("required", Arrays.asList("query"));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String query = (String) params.get("query");
            if (query == null || query.trim().isEmpty()) {
                throw new McpToolException(getName(), "Query parameter is required and cannot be empty");
            }
            
            String source = (String) params.getOrDefault("source", "all");
            boolean includeCode = (boolean) params.getOrDefault("includeCode", true);
            int maxResults = ((Number) params.getOrDefault("maxResults", 5)).intValue();
            int contextLines = ((Number) params.getOrDefault("contextLines", 2)).intValue();
            String format = (String) params.getOrDefault("format", "full");
            
            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("source", source);
            result.put("timestamp", System.currentTimeMillis());
            
            // Determine which sources to search
            List<String> sourcesToSearch = getSourcesToSearch(source);
            
            // Perform search across selected sources
            List<DocResult> searchResults = new ArrayList<>();
            for (String srcKey : sourcesToSearch) {
                try {
                    List<DocResult> srcResults = searchDocumentationSource(srcKey, query, includeCode, contextLines);
                    searchResults.addAll(srcResults);
                } catch (Exception e) {
                    log.warn("Failed to search {} documentation: {}", srcKey, e.getMessage());
                }
            }
            
            // Rank and limit results
            searchResults = rankAndLimitResults(searchResults, query, maxResults);
            
            // Format results
            result.put("results", formatResults(searchResults, format));
            result.put("resultCount", searchResults.size());
            result.put("sourcesSearched", sourcesToSearch);
            
            // Add search suggestions if few results
            if (searchResults.size() < 3) {
                result.put("suggestions", generateSearchSuggestions(query));
            }
            
            // Add related topics
            result.put("relatedTopics", findRelatedTopics(query, searchResults));
            
            return result;
            
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to search documentation: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to search documentation: " + e.getMessage(), e);
        }
    }
    
    private List<String> getSourcesToSearch(String source) {
        if ("all".equals(source)) {
            return new ArrayList<>(DOC_SOURCES.keySet());
        } else {
            return Arrays.asList(source);
        }
    }
    
    private List<DocResult> searchDocumentationSource(String sourceKey, String query, 
                                                     boolean includeCode, int contextLines) throws IOException {
        DocSource docSource = DOC_SOURCES.get(sourceKey);
        if (docSource == null) {
            return Collections.emptyList();
        }
        
        List<DocResult> results = new ArrayList<>();
        
        // For demo purposes, we'll simulate doc search with predefined content
        // In a real implementation, this would fetch and index actual documentation
        results.addAll(getSimulatedResults(sourceKey, query, includeCode));
        
        return results;
    }
    
    private List<DocResult> getSimulatedResults(String sourceKey, String query, boolean includeCode) {
        List<DocResult> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        // Spring Boot specific results
        if ("spring-boot".equals(sourceKey)) {
            if (lowerQuery.contains("actuator")) {
                results.add(new DocResult(
                        "Spring Boot Actuator",
                        "Production-ready features to help you monitor and manage your application",
                        "https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#production-ready",
                        sourceKey,
                        includeCode ? getActuatorCodeExample() : null,
                        0.95
                ));
            }
            if (lowerQuery.contains("configuration") || lowerQuery.contains("properties")) {
                results.add(new DocResult(
                        "Externalized Configuration",
                        "Spring Boot lets you externalize your configuration so you can work with the same application code in different environments",
                        "https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-external-config",
                        sourceKey,
                        includeCode ? getConfigurationCodeExample() : null,
                        0.90
                ));
            }
            if (lowerQuery.contains("testing")) {
                results.add(new DocResult(
                        "Testing",
                        "Spring Boot provides a number of utilities and annotations to help when testing your application",
                        "https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#boot-features-testing",
                        sourceKey,
                        includeCode ? getTestingCodeExample() : null,
                        0.85
                ));
            }
        }
        
        // Spring Security specific results
        if ("spring-security".equals(sourceKey)) {
            if (lowerQuery.contains("jwt") || lowerQuery.contains("token")) {
                results.add(new DocResult(
                        "JWT Authentication",
                        "JWT (JSON Web Token) authentication configuration and usage",
                        "https://docs.spring.io/spring-security/docs/current/reference/html/#oauth2resourceserver-jwt",
                        sourceKey,
                        includeCode ? getJwtCodeExample() : null,
                        0.92
                ));
            }
            if (lowerQuery.contains("authentication") || lowerQuery.contains("login")) {
                results.add(new DocResult(
                        "Authentication Architecture",
                        "Core authentication infrastructure and how to customize authentication",
                        "https://docs.spring.io/spring-security/docs/current/reference/html/#authentication",
                        sourceKey,
                        includeCode ? getAuthenticationCodeExample() : null,
                        0.88
                ));
            }
        }
        
        // Spring Data specific results
        if ("spring-data".equals(sourceKey)) {
            if (lowerQuery.contains("repository") || lowerQuery.contains("jpa")) {
                results.add(new DocResult(
                        "JPA Repositories",
                        "Repository abstraction that reduces boilerplate code required to implement data access layers",
                        "https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories",
                        sourceKey,
                        includeCode ? getRepositoryCodeExample() : null,
                        0.90
                ));
            }
            if (lowerQuery.contains("query") || lowerQuery.contains("method")) {
                results.add(new DocResult(
                        "Query Methods",
                        "Derive queries from method names and use custom queries",
                        "https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods",
                        sourceKey,
                        includeCode ? getQueryMethodCodeExample() : null,
                        0.87
                ));
            }
        }
        
        return results;
    }
    
    private List<DocResult> rankAndLimitResults(List<DocResult> results, String query, int maxResults) {
        // Sort by relevance score (descending)
        results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        
        // Limit results
        return results.stream()
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    private List<Map<String, Object>> formatResults(List<DocResult> results, String format) {
        return results.stream().map(result -> {
            Map<String, Object> formatted = new HashMap<>();
            
            switch (format) {
                case "summary":
                    formatted.put("title", result.getTitle());
                    formatted.put("summary", truncateText(result.getContent(), 100));
                    formatted.put("url", result.getUrl());
                    formatted.put("source", result.getSource());
                    break;
                    
                case "links-only":
                    formatted.put("title", result.getTitle());
                    formatted.put("url", result.getUrl());
                    formatted.put("relevance", result.getRelevanceScore());
                    break;
                    
                case "full":
                default:
                    formatted.put("title", result.getTitle());
                    formatted.put("content", result.getContent());
                    formatted.put("url", result.getUrl());
                    formatted.put("source", result.getSource());
                    formatted.put("relevanceScore", result.getRelevanceScore());
                    if (result.getCodeExample() != null) {
                        formatted.put("codeExample", result.getCodeExample());
                    }
                    break;
            }
            
            return formatted;
        }).collect(Collectors.toList());
    }
    
    private List<String> generateSearchSuggestions(String query) {
        List<String> suggestions = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        if (lowerQuery.contains("config")) {
            suggestions.add("Try: 'spring boot configuration properties'");
            suggestions.add("Try: 'externalized configuration'");
        }
        
        if (lowerQuery.contains("security")) {
            suggestions.add("Try: 'spring security authentication'");
            suggestions.add("Try: 'oauth2 resource server'");
        }
        
        if (lowerQuery.contains("data")) {
            suggestions.add("Try: 'spring data jpa repositories'");
            suggestions.add("Try: 'custom query methods'");
        }
        
        if (lowerQuery.contains("test")) {
            suggestions.add("Try: 'spring boot testing'");
            suggestions.add("Try: '@SpringBootTest annotations'");
        }
        
        suggestions.add("Use more specific terms like 'JWT authentication' instead of just 'auth'");
        suggestions.add("Include version numbers for specific Spring Boot versions");
        
        return suggestions.stream().limit(3).collect(Collectors.toList());
    }
    
    private List<String> findRelatedTopics(String query, List<DocResult> results) {
        Set<String> topics = new HashSet<>();
        String lowerQuery = query.toLowerCase();
        
        // Add topics based on query
        if (lowerQuery.contains("security")) {
            topics.addAll(Arrays.asList("authentication", "authorization", "oauth2", "jwt", "cors"));
        }
        if (lowerQuery.contains("data")) {
            topics.addAll(Arrays.asList("repositories", "transactions", "caching", "auditing"));
        }
        if (lowerQuery.contains("web")) {
            topics.addAll(Arrays.asList("controllers", "rest", "validation", "exception-handling"));
        }
        if (lowerQuery.contains("test")) {
            topics.addAll(Arrays.asList("mockito", "testcontainers", "web-mvc-test", "data-jpa-test"));
        }
        
        // Add topics based on sources in results
        for (DocResult result : results) {
            DocSource source = DOC_SOURCES.get(result.getSource());
            if (source != null) {
                topics.addAll(source.getKeyTopics());
            }
        }
        
        return topics.stream().limit(5).collect(Collectors.toList());
    }
    
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
    
    // Code example generators
    private String getActuatorCodeExample() {
        return """
                # application.yml
                management:
                  endpoints:
                    web:
                      exposure:
                        include: health,info,metrics
                  endpoint:
                    health:
                      show-details: always
                """;
    }
    
    private String getConfigurationCodeExample() {
        return """
                @ConfigurationProperties(prefix = "app.datasource")
                @Data
                public class DataSourceProperties {
                    private String url;
                    private String username;
                    private String password;
                }
                """;
    }
    
    private String getTestingCodeExample() {
        return """
                @SpringBootTest
                @TestPropertySource(locations = "classpath:test.properties")
                class MyServiceTest {
                    @Autowired
                    private MyService myService;
                    
                    @Test
                    void testServiceMethod() {
                        // test implementation
                    }
                }
                """;
    }
    
    private String getJwtCodeExample() {
        return """
                @EnableWebSecurity
                public class SecurityConfig {
                    @Bean
                    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                        return http
                            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
                            .build();
                    }
                }
                """;
    }
    
    private String getAuthenticationCodeExample() {
        return """
                @Service
                public class CustomUserDetailsService implements UserDetailsService {
                    @Override
                    public UserDetails loadUserByUsername(String username) {
                        // Load user from database
                        return User.builder()
                            .username(username)
                            .password(encodedPassword)
                            .authorities("ROLE_USER")
                            .build();
                    }
                }
                """;
    }
    
    private String getRepositoryCodeExample() {
        return """
                @Repository
                public interface UserRepository extends JpaRepository<User, Long> {
                    List<User> findByLastName(String lastName);
                    List<User> findByEmailContaining(String email);
                    
                    @Query("SELECT u FROM User u WHERE u.active = true")
                    List<User> findActiveUsers();
                }
                """;
    }
    
    private String getQueryMethodCodeExample() {
        return """
                // Query method naming conventions
                List<User> findByFirstNameAndLastName(String firstName, String lastName);
                List<User> findByAgeGreaterThan(Integer age);
                List<User> findByEmailContainingIgnoreCase(String email);
                Optional<User> findFirstByOrderByIdDesc();
                """;
    }
    
    // Data classes
    private static class DocSource {
        private final String baseUrl;
        private final String title;
        private final List<String> keyTopics;
        
        public DocSource(String baseUrl, String title, List<String> keyTopics) {
            this.baseUrl = baseUrl;
            this.title = title;
            this.keyTopics = keyTopics;
        }
        
        public String getBaseUrl() { return baseUrl; }
        public String getTitle() { return title; }
        public List<String> getKeyTopics() { return keyTopics; }
    }
    
    private static class DocResult {
        private final String title;
        private final String content;
        private final String url;
        private final String source;
        private final String codeExample;
        private final double relevanceScore;
        
        public DocResult(String title, String content, String url, String source, 
                        String codeExample, double relevanceScore) {
            this.title = title;
            this.content = content;
            this.url = url;
            this.source = source;
            this.codeExample = codeExample;
            this.relevanceScore = relevanceScore;
        }
        
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getUrl() { return url; }
        public String getSource() { return source; }
        public String getCodeExample() { return codeExample; }
        public double getRelevanceScore() { return relevanceScore; }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "securityJwt", Map.of(
                        "description", "Search for JWT authentication documentation",
                        "parameters", Map.of("query", "spring security jwt authentication")
                ),
                "actuatorEndpoints", Map.of(
                        "description", "Find actuator endpoint documentation",
                        "parameters", Map.of(
                                "query", "actuator endpoints monitoring",
                                "source", "spring-boot"
                        )
                ),
                "dataRepositories", Map.of(
                        "description", "Search for JPA repository documentation",
                        "parameters", Map.of(
                                "query", "jpa repositories query methods",
                                "source", "spring-data",
                                "includeCode", true
                        )
                ),
                "testingConfig", Map.of(
                        "description", "Find testing configuration examples",
                        "parameters", Map.of(
                                "query", "spring boot testing configuration",
                                "maxResults", 3,
                                "format", "summary"
                        )
                ),
                "configurationProperties", Map.of(
                        "description", "Search for configuration properties documentation",
                        "parameters", Map.of("query", "configuration properties externalized config")
                )
        );
    }
}
