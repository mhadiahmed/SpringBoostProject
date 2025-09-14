# Spring Boot Core Guidelines

## Overview

These guidelines provide comprehensive best practices for Spring Boot development, focusing on project structure, configuration management, dependency injection patterns, error handling strategies, and testing approaches.

## Project Structure Best Practices

### Standard Maven/Gradle Project Layout

```
src/
├── main/
│   ├── java/
│   │   └── com/company/project/
│   │       ├── ProjectApplication.java          # Main application class
│   │       ├── config/                          # Configuration classes
│   │       │   ├── SecurityConfig.java
│   │       │   ├── DatabaseConfig.java
│   │       │   └── WebConfig.java
│   │       ├── controller/                      # REST controllers
│   │       │   └── api/                         # API versioning
│   │       ├── service/                         # Business logic
│   │       │   └── impl/                        # Service implementations
│   │       ├── repository/                      # Data access layer
│   │       ├── model/                           # Domain entities
│   │       │   ├── entity/                      # JPA entities
│   │       │   └── dto/                         # Data Transfer Objects
│   │       ├── exception/                       # Custom exceptions
│   │       └── util/                           # Utility classes
│   └── resources/
│       ├── application.yml                      # Main configuration
│       ├── application-{profile}.yml            # Profile-specific configs
│       ├── db/migration/                        # Database migrations
│       └── static/                             # Static web assets
└── test/
    ├── java/                                   # Test classes mirror main structure
    └── resources/
        └── application-test.yml                # Test configuration
```

### Package Organization Guidelines

1. **Domain-Driven Packaging**: Organize by business domain rather than technical layers
2. **Consistent Naming**: Use clear, descriptive package names
3. **Separation of Concerns**: Keep related functionality together
4. **API Versioning**: Use `/api/v1/`, `/api/v2/` for REST endpoints

## Configuration Management

### Application Configuration Hierarchy

```yaml
# application.yml - Base configuration
spring:
  application:
    name: ${APP_NAME:my-application}
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:default}
  
  datasource:
    url: ${DATABASE_URL:jdbc:h2:mem:testdb}
    username: ${DATABASE_USERNAME:sa}
    password: ${DATABASE_PASSWORD:}
    
  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:validate}
    show-sql: ${JPA_SHOW_SQL:false}
    
server:
  port: ${SERVER_PORT:8080}
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    com.company.project: ${LOG_LEVEL:INFO}
    org.springframework.security: ${SECURITY_LOG_LEVEL:WARN}
```

### Profile-Specific Configuration

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:h2:mem:devdb
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    
logging:
  level:
    com.company.project: DEBUG
    org.springframework.web: DEBUG

# application-prod.yml  
spring:
  datasource:
    url: ${DATABASE_URL}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    
logging:
  level:
    com.company.project: INFO
    org.springframework.web: WARN
```

### Configuration Properties Pattern

```java
@ConfigurationProperties(prefix = "app")
@Data
@Validated
public class ApplicationProperties {
    
    @NotBlank
    private String name;
    
    @Valid
    private Database database = new Database();
    
    @Valid
    private Security security = new Security();
    
    @Data
    public static class Database {
        @Min(1)
        @Max(100)
        private int maxConnections = 10;
        
        @NotNull
        private Duration connectionTimeout = Duration.ofSeconds(30);
    }
    
    @Data
    public static class Security {
        @NotBlank
        private String jwtSecret;
        
        @Positive
        private long jwtExpirationMs = 86400000; // 24 hours
    }
}
```

## Dependency Injection Patterns

### Constructor Injection (Recommended)

```java
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor injection - mandatory dependencies
    public UserService(UserRepository userRepository, 
                      EmailService emailService,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }
}
```

### Optional Dependencies

```java
@Service
public class NotificationService {
    
    private final EmailService emailService;
    private final Optional<SmsService> smsService;
    
    public NotificationService(EmailService emailService,
                              @Autowired(required = false) SmsService smsService) {
        this.emailService = emailService;
        this.smsService = Optional.ofNullable(smsService);
    }
    
    public void sendNotification(String message, User user) {
        emailService.send(message, user.getEmail());
        smsService.ifPresent(sms -> sms.send(message, user.getPhone()));
    }
}
```

### Bean Configuration

```java
@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
public class ApplicationConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("users", "products");
    }
    
    @Bean
    @Profile("!test")
    public EmailService emailService(ApplicationProperties properties) {
        return new SmtpEmailService(properties.getEmail());
    }
    
    @Bean
    @Profile("test")
    public EmailService mockEmailService() {
        return new MockEmailService();
    }
}
```

## Error Handling Strategies

### Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ErrorResponse.builder()
                .error("ENTITY_NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        return ErrorResponse.builder()
                .error("DATA_CONFLICT")
                .message("Resource conflict occurred")
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ErrorResponse.builder()
                .error("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .timestamp(Instant.now())
                .build();
    }
}
```

### Custom Exceptions

```java
public class BusinessException extends RuntimeException {
    private final String errorCode;
    
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

public class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String entityType, Object id) {
        super("ENTITY_NOT_FOUND", 
              String.format("%s with id %s not found", entityType, id));
    }
}
```

### Error Response DTO

```java
@Data
@Builder
public class ErrorResponse {
    private String error;
    private String message;
    private String path;
    private Instant timestamp;
    private Map<String, Object> details;
    
    public static ErrorResponse of(String error, String message) {
        return ErrorResponse.builder()
                .error(error)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
```

## Testing Approaches

### Unit Testing with JUnit 5

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    @DisplayName("Should create user successfully")
    void shouldCreateUserSuccessfully() {
        // Given
        CreateUserRequest request = new CreateUserRequest("john@example.com", "password");
        User savedUser = User.builder()
                .id(1L)
                .email("john@example.com")
                .build();
        
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // When
        User result = userService.createUser(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        verify(emailService).sendWelcomeEmail(savedUser);
    }
    
    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
        // Given
        CreateUserRequest request = new CreateUserRequest("existing@example.com", "password");
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Email already exists");
    }
}
```

### Integration Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UserControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    void shouldCreateUserSuccessfully() {
        // Given
        CreateUserRequest request = new CreateUserRequest("test@example.com", "password");
        
        // When
        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                "/api/v1/users", request, UserResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getEmail()).isEqualTo("test@example.com");
        
        // Verify in database
        Optional<User> savedUser = userRepository.findByEmail("test@example.com");
        assertThat(savedUser).isPresent();
    }
}
```

### Test Configuration

```java
@TestConfiguration
public class TestConfig {
    
    @Bean
    @Primary
    public EmailService mockEmailService() {
        return Mockito.mock(EmailService.class);
    }
    
    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(Instant.parse("2023-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}
```

## Application Lifecycle Management

### Main Application Class

```java
@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties.class)
@EnableJpaRepositories
@EnableScheduling
public class MyApplication {
    
    public static void main(String[] args) {
        System.setProperty("spring.application.name", "my-application");
        SpringApplication.run(MyApplication.class, args);
    }
    
    @Bean
    public ApplicationRunner applicationRunner() {
        return args -> {
            log.info("Application started successfully");
        };
    }
}
```

### Health Checks

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("validationQuery", "SELECT 1")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("database", "Unavailable")
                    .withException(e)
                    .build();
        }
    }
}
```

## Best Practices Summary

### DO:
- Use constructor injection for mandatory dependencies
- Implement proper error handling with custom exceptions
- Use configuration properties for external configuration
- Write comprehensive tests (unit + integration)
- Follow consistent package structure
- Use profiles for environment-specific configuration
- Implement health checks for critical dependencies
- Use validation annotations for input validation

### DON'T:
- Use field injection (@Autowired on fields)
- Expose internal implementation details in APIs
- Hard-code configuration values
- Ignore exception handling
- Mix business logic with presentation logic
- Use @ComponentScan with broad packages
- Put all configuration in a single file
- Skip testing critical business logic

### Performance Considerations:
- Use connection pooling for databases
- Implement caching for frequently accessed data
- Use async processing for long-running operations
- Monitor application metrics with Actuator
- Profile application under load
- Use appropriate HTTP status codes
- Implement pagination for large datasets

### Security Considerations:
- Never log sensitive information
- Use HTTPS in production
- Implement proper authentication/authorization
- Validate all input data
- Use parameterized queries to prevent SQL injection
- Implement rate limiting for APIs
- Keep dependencies up to date
- Use secure defaults for configuration
