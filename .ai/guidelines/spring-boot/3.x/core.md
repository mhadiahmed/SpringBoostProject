# Spring Boot 3.x Specific Guidelines

## Overview

Spring Boot 3.x brings significant changes and improvements including Java 17+ requirement, Jakarta EE migration, native compilation support, and enhanced observability. This guide covers version-specific patterns and migration considerations.

## Key Changes in Spring Boot 3.x

### Java Version Requirements

```java
// Minimum Java 17 required
// Recommended: Java 21 LTS for production

// Module system support (optional)
module com.company.application {
    requires spring.boot.starter.web;
    requires spring.boot.starter.data.jpa;
    requires spring.boot.starter.security;
    
    exports com.company.application.api;
    
    opens com.company.application.entity to 
        org.hibernate.orm.core, com.fasterxml.jackson.databind;
}
```

### Jakarta EE Migration

```java
// BEFORE Spring Boot 2.x (javax.*)
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotBlank;

// AFTER Spring Boot 3.x (jakarta.*)
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;

@Entity
public class User {
    @Id
    private Long id;
    
    @NotBlank
    private String email;
    
    // Rest of the entity
}
```

## Native Compilation Support

### GraalVM Native Image Configuration

```java
// Application class with native hints
@SpringBootApplication
@RegisterReflectionForBinding({UserRequest.class, UserResponse.class})
public class SpringBoostApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SpringBoostApplication.class, args);
    }
}

// Runtime hints for reflection
@Component
public class NativeRuntimeHints implements RuntimeHintsRegistrar {
    
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register classes that need reflection
        hints.reflection()
            .registerType(TypeReference.of(UserService.class), 
                MemberCategory.INVOKE_PUBLIC_METHODS)
            .registerType(TypeReference.of(User.class), 
                MemberCategory.DECLARED_FIELDS);
        
        // Register resources
        hints.resources()
            .registerPattern("static/**")
            .registerPattern("templates/**");
        
        // Register serialization
        hints.serialization()
            .registerType(UserRequest.class)
            .registerType(UserResponse.class);
    }
}
```

### Build Configuration for Native

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <configuration>
        <buildArgs>
            <buildArg>--no-fallback</buildArg>
            <buildArg>--enable-preview</buildArg>
            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
        </buildArgs>
    </configuration>
</plugin>

<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <image>
            <buildpacks>
                <buildpack>paketobuildpacks/java-native-image</buildpack>
            </buildpacks>
        </image>
    </configuration>
</plugin>
```

### Native-Friendly Code Patterns

```java
// Avoid reflection-heavy frameworks
// Use compile-time dependency injection when possible

@Component
public class UserService {
    
    private final UserRepository userRepository;
    
    // Constructor injection - native-friendly
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    // Avoid field injection in native apps
    // @Autowired
    // private UserRepository userRepository; // Not recommended for native
}

// Use @ConfigurationProperties instead of @Value
@ConfigurationProperties(prefix = "app.user")
@Data
public class UserProperties {
    private int maxAge = 120;
    private String defaultRole = "USER";
    private List<String> allowedDomains = new ArrayList<>();
}

// Avoid dynamic class loading
// Use static factory methods instead
public class ServiceFactory {
    
    // Native-friendly
    public static EmailService createEmailService(String type) {
        return switch (type.toLowerCase()) {
            case "smtp" -> new SmtpEmailService();
            case "ses" -> new SesEmailService();
            default -> new MockEmailService();
        };
    }
    
    // Avoid in native apps
    // public static EmailService createEmailService(String className) {
    //     return (EmailService) Class.forName(className).getDeclaredConstructor().newInstance();
    // }
}
```

## Enhanced Observability

### Micrometer Observation API

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final ObservationRegistry observationRegistry;
    
    public User createUser(CreateUserRequest request) {
        return Observation
                .createNotStarted("user.create", observationRegistry)
                .contextualName("user-creation")
                .lowCardinalityKeyValue("operation", "create")
                .highCardinalityKeyValue("email.domain", extractDomain(request.getEmail()))
                .observe(() -> {
                    // Business logic here
                    User user = User.builder()
                            .email(request.getEmail())
                            .firstName(request.getFirstName())
                            .lastName(request.getLastName())
                            .build();
                    
                    return userRepository.save(user);
                });
    }
    
    @Observed(name = "user.fetch", contextualName = "user-by-id")
    public User getUserById(@Observed.Argument Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
    }
    
    private String extractDomain(String email) {
        return email.substring(email.indexOf('@') + 1);
    }
}
```

### HTTP Observability

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    @GetMapping("/{id}")
    @Observed(name = "http.user.get")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(UserResponse.from(user));
    }
    
    // Custom observation context
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        
        return Observation
                .createNotStarted("http.user.create", observationRegistry)
                .contextualName("user-creation-endpoint")
                .lowCardinalityKeyValue("method", "POST")
                .lowCardinalityKeyValue("endpoint", "/api/v1/users")
                .highCardinalityKeyValue("user.agent", httpRequest.getHeader("User-Agent"))
                .observe(() -> {
                    User user = userService.createUser(request);
                    UserResponse response = UserResponse.from(user);
                    
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .location(createLocationUri(user.getId()))
                            .body(response);
                });
    }
}
```

### Configuration for Observability

```yaml
# application.yml
management:
  observations:
    key-values:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
  
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
        user.create: true
        user.fetch: true
  
  tracing:
    sampling:
      probability: 1.0 # 100% sampling for development
    zipkin:
      tracing:
        endpoint: http://localhost:9411/api/v2/spans
  
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,traces

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

## Problem Details (RFC 7807) Support

```java
@RestControllerAdvice
public class ProblemDetailsExceptionHandler {
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        
        problemDetail.setTitle("Entity Not Found");
        problemDetail.setType(URI.create("https://api.example.com/errors/entity-not-found"));
        problemDetail.setProperty("entity", ex.getEntityType());
        problemDetail.setProperty("id", ex.getEntityId());
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create("https://api.example.com/errors/validation"));
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        
        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", Instant.now());
        
        return problemDetail;
    }
}
```

## HTTP Interfaces (Declarative HTTP Clients)

```java
// Define HTTP interface
@HttpExchange("/api/v1/users")
public interface UserApiClient {
    
    @GetExchange("/{id}")
    User getUser(@PathVariable Long id);
    
    @PostExchange
    User createUser(@RequestBody CreateUserRequest request);
    
    @PutExchange("/{id}")
    User updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request);
    
    @DeleteExchange("/{id}")
    void deleteUser(@PathVariable Long id);
    
    @GetExchange
    List<User> getUsers(@RequestParam(required = false) String name,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size);
}

// Configuration
@Configuration
public class HttpClientsConfig {
    
    @Bean
    public UserApiClient userApiClient() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.external-service.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builder(WebClientAdapter.forClient(webClient))
                .build();
        
        return factory.createClient(UserApiClient.class);
    }
}

// Usage in service
@Service
@RequiredArgsConstructor
public class ExternalUserService {
    
    private final UserApiClient userApiClient;
    
    @Retryable(value = {Exception.class}, maxAttempts = 3)
    public User fetchExternalUser(Long id) {
        return userApiClient.getUser(id);
    }
    
    @CircuitBreaker(name = "external-users", fallbackMethod = "fallbackCreateUser")
    public User createExternalUser(CreateUserRequest request) {
        return userApiClient.createUser(request);
    }
    
    public User fallbackCreateUser(CreateUserRequest request, Exception ex) {
        log.warn("Fallback for user creation: {}", ex.getMessage());
        // Return cached or default user
        return createDefaultUser();
    }
}
```

## Virtual Threads Support (Java 21+)

```java
// Enable virtual threads
@Configuration
@EnableAsync
public class VirtualThreadConfig {
    
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
    
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}

// Using virtual threads in services
@Service
@RequiredArgsConstructor
public class AsyncUserService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    
    @Async
    public CompletableFuture<Void> processUserRegistration(User user) {
        // This will run on a virtual thread
        try {
            // Simulate some I/O operations
            emailService.sendWelcomeEmail(user);
            
            // Update user status
            user.setEmailSent(true);
            userRepository.save(user);
            
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    public List<User> processUsersInParallel(List<Long> userIds) {
        // Virtual threads make this very lightweight
        return userIds.parallelStream()
                .map(this::processUser)
                .collect(Collectors.toList());
    }
    
    private User processUser(Long userId) {
        // Each parallel operation runs on a virtual thread
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            // Simulate some processing
            performExpensiveOperation(user);
        }
        return user;
    }
}
```

## Spring Boot 3.x Testing Improvements

### TestContainers Integration

```java
@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {
    
    @Container
    @ServiceConnection  // New in Spring Boot 3.1
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Autowired
    private UserService userService;
    
    @Test
    void shouldCreateUser() {
        // Test implementation - no need for @DynamicPropertySource
        // @ServiceConnection automatically configures the datasource
    }
}

// Custom container configuration
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {
    
    @Bean
    @RestartScope  // New scope for test containers
    @ServiceConnection
    public PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
    }
}
```

### MockWebServer Integration

```java
@SpringBootTest
class ExternalApiIntegrationTest {
    
    @RegisterExtension
    static MockWebServerExtension mockWebServer = new MockWebServerExtension();
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("external.api.url", () -> mockWebServer.getBaseUrl());
    }
    
    @Test
    void shouldCallExternalApi() {
        // Mock response
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                    {
                        "id": 1,
                        "name": "John Doe"
                    }
                    """)
                .addHeader("Content-Type", "application/json"));
        
        // Test implementation
    }
}
```

## Migration Guidelines

### From Spring Boot 2.x to 3.x

1. **Java Version Upgrade**
```bash
# Update Java version in pom.xml
<java.version>17</java.version>

# Or use Java 21 for latest features
<java.version>21</java.version>
```

2. **Package Migration Script**
```bash
# Use OpenRewrite for automated migration
./mvnw org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-spring:LATEST \
  -Drewrite.activeRecipes=org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0
```

3. **Manual Changes Required**
```java
// Update security configuration
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/api/public/**").permitAll()  // Updated method
                    .anyRequest().authenticated())
                .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
                .build();
    }
}

// Update actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics  # More restrictive by default
```

4. **Dependencies to Update**
```xml
<!-- Update Spring Boot parent -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
    <relativePath/>
</parent>

<!-- Update other dependencies -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```

## Best Practices for Spring Boot 3.x

### Performance Optimization

```java
// Use @RegisterReflectionForBinding for native compilation
@RegisterReflectionForBinding({
    UserRequest.class,
    UserResponse.class,
    ErrorResponse.class
})
@SpringBootApplication
public class Application {
    
    // Use @ConditionalOnMissingBean for optional dependencies
    @Bean
    @ConditionalOnMissingBean
    public UserService userService() {
        return new DefaultUserService();
    }
}

// Optimize database queries for native compilation
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Use explicit queries instead of derived queries when possible
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.active = true")
    Optional<User> findActiveByEmail(@Param("email") String email);
}
```

### Security Enhancements

```java
// Use method security with SpEL expressions
@PreAuthorize("hasRole('ADMIN') or @userService.isOwner(authentication.name, #userId)")
public User updateUser(Long userId, UpdateUserRequest request) {
    // Implementation
}

// Configure CORS with new methods
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("https://*.example.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

### Monitoring and Observability

```java
// Custom metrics with Micrometer
@Component
@RequiredArgsConstructor
public class UserMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter userRegistrations;
    private final Timer userCreationTimer;
    
    public UserMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.userRegistrations = Counter.builder("user.registrations")
                .description("Number of user registrations")
                .register(meterRegistry);
        this.userCreationTimer = Timer.builder("user.creation.time")
                .description("Time to create a user")
                .register(meterRegistry);
    }
    
    public void recordUserRegistration() {
        userRegistrations.increment();
    }
    
    public void recordUserCreationTime(Duration duration) {
        userCreationTimer.record(duration);
    }
}
```

Spring Boot 3.x provides significant improvements in performance, observability, and developer experience while maintaining the familiar programming model. The migration requires careful attention to Jakarta EE namespace changes and dependency updates, but the benefits include better native compilation support, enhanced security, and improved monitoring capabilities.
