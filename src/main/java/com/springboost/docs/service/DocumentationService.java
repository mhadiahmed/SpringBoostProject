package com.springboost.docs.service;

import com.springboost.docs.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing and processing Spring Boot documentation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentationService {
    
    private final EmbeddingsService embeddingsService;
    private final WebClient.Builder webClientBuilder;
    
    // In-memory storage for documentation chunks
    private final Map<String, DocumentChunk> documentIndex = new ConcurrentHashMap<>();
    
    // Documentation sources configuration
    private final Map<String, String> documentationSources = Map.of(
            "spring-boot-3.x", "https://docs.spring.io/spring-boot/docs/current/reference/html/",
            "spring-boot-2.x", "https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/",
            "spring-security-6.x", "https://docs.spring.io/spring-security/reference/",
            "spring-security-5.x", "https://docs.spring.io/spring-security/site/docs/5.8.x/reference/html5/",
            "spring-data-3.x", "https://docs.spring.io/spring-data/jpa/docs/current/reference/html/",
            "spring-data-2.x", "https://docs.spring.io/spring-data/jpa/docs/2.7.x/reference/html/"
    );
    
    // Patterns for extracting content
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[a-zA-Z]*\\n([\\s\\S]*?)```");
    private static final Pattern JAVA_CODE_PATTERN = Pattern.compile("@[A-Za-z]+|public class|private|protected|import ");
    private static final Pattern CONFIG_PATTERN = Pattern.compile("(application\\.yml|application\\.properties|@Configuration)");
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Documentation Service with {} sources", documentationSources.size());
        
        // For demo purposes, we'll create some sample documentation chunks
        initializeSampleDocumentation();
    }
    
    /**
     * Initialize sample documentation for testing and demonstration
     */
    private void initializeSampleDocumentation() {
        List<DocumentChunk> sampleChunks = List.of(
                createSampleChunk("Spring Boot Auto-Configuration", 
                        "Spring Boot auto-configuration attempts to automatically configure your Spring application based on the jar dependencies that you have added. " +
                        "For example, if HSQLDB is on your classpath, and you have not manually configured any database connection beans, " +
                        "then Spring Boot auto-configures an in-memory database. " +
                        "@EnableAutoConfiguration annotation enables auto-configuration. " +
                        "You can exclude specific auto-configuration classes using exclude attribute.",
                        "https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration",
                        "spring-boot", "3.2.0", "core"),
                
                createSampleChunk("Spring Security Configuration",
                        "Spring Security provides comprehensive security services for Java EE-based enterprise software applications. " +
                        "The @EnableWebSecurity annotation enables Spring Security's web security support and provides the Spring MVC integration. " +
                        "To configure Spring Security, you need to create a configuration class that extends WebSecurityConfigurerAdapter " +
                        "or implements WebSecurityConfigurer interface.",
                        "https://docs.spring.io/spring-security/reference/servlet/configuration/java.html",
                        "spring-security", "6.1.0", "configuration"),
                
                createSampleChunk("Spring Data JPA Repositories",
                        "Spring Data JPA provides repository support for the Java Persistence API (JPA). " +
                        "It eases development of applications that need to access JPA data sources. " +
                        "The @Repository annotation indicates that the decorated class is a repository. " +
                        "You can extend JpaRepository interface to get basic CRUD operations. " +
                        "Query methods are automatically implemented based on method names.",
                        "https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories",
                        "spring-data", "3.1.0", "repositories"),
                
                createSampleChunk("Spring Boot Testing",
                        "Spring Boot provides excellent testing support with @SpringBootTest annotation. " +
                        "This annotation creates an ApplicationContext that is used in your tests. " +
                        "@TestConfiguration allows you to define additional configuration for tests. " +
                        "@MockBean annotation can be used to add mock objects to the Spring ApplicationContext. " +
                        "WebMvcTest is used for testing Spring MVC controllers.",
                        "https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing",
                        "spring-boot", "3.2.0", "testing"),
                
                createSampleChunk("Spring Boot Actuator",
                        "Spring Boot Actuator provides production-ready features to help you monitor and manage your application. " +
                        "Actuator endpoints let you monitor and interact with your application. " +
                        "Built-in endpoints include health, metrics, info, beans, env, and many more. " +
                        "You can expose endpoints over HTTP or JMX. " +
                        "Custom endpoints can be created using @Endpoint annotation.",
                        "https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html",
                        "spring-boot", "3.2.0", "actuator")
        );
        
        for (DocumentChunk chunk : sampleChunks) {
            indexDocument(chunk);
        }
        
        log.info("Initialized {} sample documentation chunks", sampleChunks.size());
    }
    
    /**
     * Create a sample documentation chunk
     */
    private DocumentChunk createSampleChunk(String title, String content, String url, 
                                          String source, String version, String category) {
        DocumentChunk chunk = DocumentChunk.create(title, content, url, source);
        chunk.setVersion(version);
        chunk.setCategory(category);
        chunk.setTags(extractTags(content));
        chunk.setCodeSnippets(extractCodeSnippets(content));
        chunk.setConfigurationExamples(extractConfigurationExamples(content));
        chunk.setChecksum(generateChecksum(content));
        
        // Generate embeddings
        List<Double> embeddings = embeddingsService.generateEmbeddings(content);
        chunk.setEmbedding(embeddings);
        chunk.setEmbeddingDimension(embeddings.size());
        
        return chunk;
    }
    
    /**
     * Index a document chunk for search
     */
    public void indexDocument(DocumentChunk chunk) {
        if (chunk.getId() == null) {
            chunk.setId(generateDocumentId(chunk));
        }
        
        documentIndex.put(chunk.getId(), chunk);
        log.debug("Indexed document: {} ({})", chunk.getTitle(), chunk.getId());
    }
    
    /**
     * Scrape documentation from a URL
     */
    public Mono<List<DocumentChunk>> scrapeDocumentation(String source, String url) {
        return webClientBuilder.build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> parseDocumentationPage(html, source, url))
                .doOnNext(chunks -> chunks.forEach(this::indexDocument))
                .doOnSuccess(chunks -> log.info("Scraped {} chunks from {}", chunks.size(), url))
                .doOnError(error -> log.error("Failed to scrape documentation from {}: {}", url, error.getMessage()));
    }
    
    /**
     * Parse an HTML documentation page into chunks
     */
    private List<DocumentChunk> parseDocumentationPage(String html, String source, String baseUrl) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        try {
            Document doc = Jsoup.parse(html);
            
            // Extract main content sections
            Elements sections = doc.select("section, div.sect1, div.sect2, h1, h2, h3");
            
            for (Element section : sections) {
                String title = extractTitle(section);
                String content = extractContent(section);
                
                if (isValidContent(title, content)) {
                    DocumentChunk chunk = createDocumentChunk(title, content, baseUrl, source);
                    chunks.add(chunk);
                }
            }
            
            // If no sections found, try to extract by paragraphs
            if (chunks.isEmpty()) {
                Elements paragraphs = doc.select("p");
                chunks.addAll(createChunksFromParagraphs(paragraphs, source, baseUrl));
            }
            
        } catch (Exception e) {
            log.error("Failed to parse documentation page: {}", e.getMessage());
        }
        
        return chunks;
    }
    
    /**
     * Extract title from an element
     */
    private String extractTitle(Element element) {
        // Try different title selectors
        String[] titleSelectors = {"h1", "h2", "h3", ".title", ".section-title"};
        
        for (String selector : titleSelectors) {
            Element titleElement = element.selectFirst(selector);
            if (titleElement != null) {
                return titleElement.text().trim();
            }
        }
        
        // Fallback to element text if it's a header
        if (element.tagName().matches("h[1-6]")) {
            return element.text().trim();
        }
        
        return "Untitled Section";
    }
    
    /**
     * Extract content from an element
     */
    private String extractContent(Element element) {
        StringBuilder content = new StringBuilder();
        
        // Extract text content
        String text = element.text();
        if (text != null && !text.trim().isEmpty()) {
            content.append(text.trim()).append("\n\n");
        }
        
        // Extract code blocks
        Elements codeBlocks = element.select("pre, code");
        for (Element codeBlock : codeBlocks) {
            String code = codeBlock.text();
            if (code != null && !code.trim().isEmpty()) {
                content.append("```\n").append(code.trim()).append("\n```\n\n");
            }
        }
        
        return content.toString().trim();
    }
    
    /**
     * Check if content is valid for indexing
     */
    private boolean isValidContent(String title, String content) {
        return title != null && !title.trim().isEmpty() && 
               content != null && content.trim().length() > 50;
    }
    
    /**
     * Create a document chunk from extracted content
     */
    private DocumentChunk createDocumentChunk(String title, String content, String url, String source) {
        DocumentChunk chunk = DocumentChunk.create(title, content, url, source);
        
        // Enhance with metadata
        chunk.setTags(extractTags(content));
        chunk.setCodeSnippets(extractCodeSnippets(content));
        chunk.setConfigurationExamples(extractConfigurationExamples(content));
        chunk.setChecksum(generateChecksum(content));
        chunk.setCategory(inferCategory(content));
        
        // Generate embeddings
        List<Double> embeddings = embeddingsService.generateEmbeddings(content);
        chunk.setEmbedding(embeddings);
        chunk.setEmbeddingDimension(embeddings.size());
        
        return chunk;
    }
    
    /**
     * Create chunks from paragraphs when no sections are found
     */
    private List<DocumentChunk> createChunksFromParagraphs(Elements paragraphs, String source, String baseUrl) {
        List<DocumentChunk> chunks = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = "Documentation Content";
        int chunkCount = 0;
        
        for (Element p : paragraphs) {
            String text = p.text();
            if (text != null && !text.trim().isEmpty()) {
                currentContent.append(text.trim()).append("\n\n");
                
                // Create chunk every 3-5 paragraphs or when content gets too long
                if (currentContent.length() > 1000 || chunkCount % 4 == 0) {
                    if (currentContent.length() > 100) {
                        DocumentChunk chunk = createDocumentChunk(
                                currentTitle + " (Part " + (chunkCount + 1) + ")",
                                currentContent.toString().trim(),
                                baseUrl,
                                source
                        );
                        chunks.add(chunk);
                    }
                    currentContent = new StringBuilder();
                    chunkCount++;
                }
            }
        }
        
        // Add remaining content
        if (currentContent.length() > 100) {
            DocumentChunk chunk = createDocumentChunk(
                    currentTitle + " (Part " + (chunkCount + 1) + ")",
                    currentContent.toString().trim(),
                    baseUrl,
                    source
            );
            chunks.add(chunk);
        }
        
        return chunks;
    }
    
    /**
     * Extract tags from content
     */
    private List<String> extractTags(String content) {
        Set<String> tags = new HashSet<>();
        String lowerContent = content.toLowerCase();
        
        // Spring-related tags
        String[] springTerms = {
            "spring boot", "spring security", "spring data", "spring web", "spring mvc",
            "autoconfiguration", "configuration", "controller", "service", "repository",
            "bean", "component", "autowired", "dependency injection", "actuator", "testing"
        };
        
        for (String term : springTerms) {
            if (lowerContent.contains(term)) {
                tags.add(term.replace(" ", "-"));
            }
        }
        
        // Technical tags
        if (lowerContent.contains("@")) tags.add("annotations");
        if (lowerContent.contains("yaml") || lowerContent.contains("yml")) tags.add("configuration");
        if (lowerContent.contains("test")) tags.add("testing");
        if (lowerContent.contains("security")) tags.add("security");
        if (lowerContent.contains("database") || lowerContent.contains("jpa")) tags.add("database");
        
        return new ArrayList<>(tags);
    }
    
    /**
     * Extract code snippets from content
     */
    private List<String> extractCodeSnippets(String content) {
        List<String> snippets = new ArrayList<>();
        
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        while (matcher.find()) {
            snippets.add(matcher.group(1).trim());
        }
        
        // Also look for inline Java code patterns
        if (JAVA_CODE_PATTERN.matcher(content).find()) {
            // Extract lines that look like Java code
            String[] lines = content.split("\n");
            StringBuilder codeBlock = new StringBuilder();
            
            for (String line : lines) {
                if (JAVA_CODE_PATTERN.matcher(line).find()) {
                    codeBlock.append(line.trim()).append("\n");
                } else if (codeBlock.length() > 0) {
                    snippets.add(codeBlock.toString().trim());
                    codeBlock = new StringBuilder();
                }
            }
            
            if (codeBlock.length() > 0) {
                snippets.add(codeBlock.toString().trim());
            }
        }
        
        return snippets;
    }
    
    /**
     * Extract configuration examples from content
     */
    private List<String> extractConfigurationExamples(String content) {
        List<String> configs = new ArrayList<>();
        
        if (CONFIG_PATTERN.matcher(content).find()) {
            // Look for YAML or properties configuration
            String[] lines = content.split("\n");
            StringBuilder configBlock = new StringBuilder();
            
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains(":") || trimmed.contains("=") || trimmed.startsWith("#")) {
                    configBlock.append(line).append("\n");
                } else if (configBlock.length() > 0) {
                    configs.add(configBlock.toString().trim());
                    configBlock = new StringBuilder();
                }
            }
            
            if (configBlock.length() > 0) {
                configs.add(configBlock.toString().trim());
            }
        }
        
        return configs;
    }
    
    /**
     * Infer category from content
     */
    private String inferCategory(String content) {
        String lowerContent = content.toLowerCase();
        
        if (lowerContent.contains("test") || lowerContent.contains("@test")) {
            return "testing";
        } else if (lowerContent.contains("security") || lowerContent.contains("authentication")) {
            return "security";
        } else if (lowerContent.contains("database") || lowerContent.contains("jpa") || lowerContent.contains("repository")) {
            return "data";
        } else if (lowerContent.contains("controller") || lowerContent.contains("web") || lowerContent.contains("http")) {
            return "web";
        } else if (lowerContent.contains("configuration") || lowerContent.contains("properties")) {
            return "configuration";
        } else {
            return "core";
        }
    }
    
    /**
     * Generate a unique ID for a document
     */
    private String generateDocumentId(DocumentChunk chunk) {
        String checksum = chunk.getChecksum();
        // Ensure we have enough characters for substring
        String shortChecksum = checksum.length() >= 8 ? checksum.substring(0, 8) : checksum;
        return chunk.getSource() + "-" + shortChecksum;
    }
    
    /**
     * Generate checksum for content
     */
    private String generateChecksum(String content) {
        // Use absolute value and pad with zeros to ensure consistent length
        int hash = Math.abs(content.hashCode());
        return String.format("%08d", hash);
    }
    
    /**
     * Get all indexed documents
     */
    public Collection<DocumentChunk> getAllDocuments() {
        return documentIndex.values();
    }
    
    /**
     * Get document by ID
     */
    public Optional<DocumentChunk> getDocumentById(String id) {
        return Optional.ofNullable(documentIndex.get(id));
    }
    
    /**
     * Get documents by source
     */
    public List<DocumentChunk> getDocumentsBySource(String source) {
        return documentIndex.values().stream()
                .filter(chunk -> source.equals(chunk.getSource()))
                .toList();
    }
    
    /**
     * Update documentation from all configured sources
     */
    public Flux<DocumentChunk> updateAllDocumentation() {
        return Flux.fromIterable(documentationSources.entrySet())
                .flatMap(entry -> scrapeDocumentation(entry.getKey(), entry.getValue()))
                .flatMap(Flux::fromIterable);
    }
    
    /**
     * Get index statistics
     */
    public Map<String, Object> getIndexStats() {
        Map<String, Long> sourceStats = documentIndex.values().stream()
                .collect(Collectors.groupingBy(
                        DocumentChunk::getSource,
                        Collectors.counting()
                ));
        
        return Map.of(
                "totalDocuments", documentIndex.size(),
                "documentsBySources", sourceStats,
                "lastUpdated", LocalDateTime.now()
        );
    }
}
