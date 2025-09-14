# Spring Boot 2.x Specific Guidelines

## Overview

Spring Boot 2.x guidelines for maintaining legacy applications and understanding differences with Spring Boot 3.x. This covers Java 8-17 compatibility, javax.* namespace usage, and version-specific patterns.

## Java Version Compatibility

### Supported Java Versions

```java
// Spring Boot 2.x supports Java 8-17
// Recommended: Java 11 LTS for production

// Java 8 compatible patterns
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    
    public UserService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }
    
    // Java 8 Optional patterns
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    // Java 8 Stream API usage
    public List<UserResponse> getActiveUsers() {
        return userRepository.findByActiveTrue()
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }
    
    // Java 8 CompletableFuture for async operations
    @Async
    public CompletableFuture<Void> sendWelcomeEmail(User user) {
        return CompletableFuture.runAsync(() -> {
            emailService.sendWelcomeEmail(user);
        });
    }
}
```

## Javax Namespace Usage

### JPA and Persistence

```java
// Spring Boot 2.x uses javax.* packages
import javax.persistence.*;
import javax.validation.constraints.*;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    @NotBlank
    @Email
    private String email;
    
    @NotBlank
    @Size(min = 2, max = 50)
    private String firstName;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Constructors, getters, setters
}
```

### Web and Servlet API

```java
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        
        // Access request information
        String userAgent = httpRequest.getHeader("User-Agent");
        String clientIp = getClientIpAddress(httpRequest);
        
        User user = userService.createUser(request);
        UserResponse response = UserResponse.from(user);
        
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();
        
        return ResponseEntity.created(location).body(response);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### Validation

```java
import javax.validation.Valid;
import javax.validation.constraints.*;

@Data
@Builder
public class CreateUserRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;
    
    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]",
             message = "Password must contain at least one lowercase letter, one uppercase letter, one digit, and one special character")
    private String password;
    
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;
}
```

## Security Configuration (Spring Boot 2.x)

### WebSecurityConfigurerAdapter Pattern

```java
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final UserDetailsService userDetailsService;
    
    public SecurityConfig(JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                         JwtAccessDeniedHandler jwtAccessDeniedHandler,
                         UserDetailsService userDetailsService) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.userDetailsService = userDetailsService;
    }
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            .and()
            .authorizeRequests()
                .antMatchers("/api/v1/auth/**").permitAll()
                .antMatchers("/api/v1/public/**").permitAll()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers(HttpMethod.GET, "/api/v1/products/**").hasAnyRole("USER", "ADMIN")
                .antMatchers(HttpMethod.POST, "/api/v1/products/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
    
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
```

### OAuth2 Resource Server (Spring Boot 2.x)

```java
@Configuration
@EnableResourceServer
public class OAuth2ResourceServerConfig extends ResourceServerConfigurerAdapter {
    
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/api/v1/public/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .oauth2ResourceServer()
                .jwt();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation("https://your-oauth-provider.com");
    }
}
```

## Actuator Configuration (Spring Boot 2.x)

### Actuator Endpoints

```yaml
# application.yml - Spring Boot 2.x
management:
  endpoints:
    web:
      exposure:
        include: "*"  # More permissive by default in 2.x
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
  info:
    git:
      mode: full
  metrics:
    export:
      prometheus:
        enabled: true
```

### Custom Health Indicators

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
            if (connection.isValid(1)) {
                return Health.up()
                        .withDetail("database", "Available")
                        .withDetail("validationQuery", "SELECT 1")
                        .build();
            } else {
                return Health.down()
                        .withDetail("database", "Connection validation failed")
                        .build();
            }
        } catch (SQLException ex) {
            return Health.down()
                    .withDetail("database", "Unavailable")
                    .withException(ex)
                    .build();
        }
    }
}
```

## Testing in Spring Boot 2.x

### Test Slices

```java
@RunWith(SpringRunner.class)  // JUnit 4 style
@WebMvcTest(UserController.class)
public class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    public void shouldCreateUser() throws Exception {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("password123")
                .build();
        
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(user);
        
        // When & Then
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }
}

// JUnit 5 style (available in Spring Boot 2.2+)
@ExtendWith(SpringExtension.class)
@WebMvcTest(UserController.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    void shouldCreateUser() throws Exception {
        // Test implementation
    }
}
```

### Integration Testing

```java
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
public class UserServiceIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    public void shouldCreateUserSuccessfully() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("integration@test.com")
                .firstName("Integration")
                .lastName("Test")
                .password("password123")
                .build();
        
        // When
        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                "/api/v1/users", request, UserResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getEmail()).isEqualTo("integration@test.com");
        
        // Verify in database
        Optional<User> savedUser = userRepository.findByEmail("integration@test.com");
        assertThat(savedUser).isPresent();
    }
}
```

## Reactive Programming (Spring Boot 2.x)

### WebFlux Support

```java
@RestController
@RequestMapping("/api/v1/reactive/users")
public class ReactiveUserController {
    
    private final ReactiveUserService userService;
    
    public ReactiveUserController(ReactiveUserService userService) {
        this.userService = userService;
    }
    
    @GetMapping
    public Flux<UserResponse> getAllUsers() {
        return userService.getAllUsers()
                .map(UserResponse::from);
    }
    
    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserResponse>> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    public Mono<ResponseEntity<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request)
                .map(user -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(UserResponse.from(user)));
    }
    
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<UserResponse> streamUsers() {
        return userService.streamUsers()
                .map(UserResponse::from);
    }
}

@Service
public class ReactiveUserService {
    
    private final ReactiveUserRepository userRepository;
    
    public ReactiveUserService(ReactiveUserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public Flux<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    public Mono<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    public Mono<User> createUser(CreateUserRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();
        
        return userRepository.save(user);
    }
    
    public Flux<User> streamUsers() {
        return Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> userRepository.findAll());
    }
}
```

## Configuration Properties (Spring Boot 2.x)

### Configuration Binding

```java
@Component
@ConfigurationProperties(prefix = "app.user")
@Data
@Validated
public class UserProperties {
    
    @NotBlank
    private String defaultRole = "USER";
    
    @Min(1)
    @Max(150)
    private int maxAge = 120;
    
    @Valid
    private List<String> allowedDomains = new ArrayList<>();
    
    @Valid
    private Security security = new Security();
    
    @Data
    @Validated
    public static class Security {
        
        @NotBlank
        private String passwordPattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";
        
        @Min(1)
        private int maxLoginAttempts = 5;
        
        @NotNull
        private Duration lockoutDuration = Duration.ofMinutes(30);
    }
}

// Enable configuration properties
@Configuration
@EnableConfigurationProperties(UserProperties.class)
public class AppConfig {
    // Configuration beans
}
```

## Error Handling (Spring Boot 2.x)

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage()));
        
        return ErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message("Validation failed")
                .details(errors)
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(EntityNotFoundException ex) {
        return ErrorResponse.builder()
                .error("ENTITY_NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
    
    // Error response with basic structure (no Problem Details support)
    @Data
    @Builder
    public static class ErrorResponse {
        private String error;
        private String message;
        private Map<String, Object> details;
        private Instant timestamp;
    }
}
```

## Metrics and Monitoring (Spring Boot 2.x)

### Micrometer Integration

```java
@Service
public class UserMetricsService {
    
    private final MeterRegistry meterRegistry;
    private final Counter userCreatedCounter;
    private final Timer userCreationTimer;
    
    public UserMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.userCreatedCounter = Counter.builder("users.created")
                .description("Number of users created")
                .register(meterRegistry);
        this.userCreationTimer = Timer.builder("users.creation.time")
                .description("Time taken to create a user")
                .register(meterRegistry);
    }
    
    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        userCreatedCounter.increment();
        
        // Add tags for more detailed metrics
        Counter.builder("users.created.by.domain")
                .tag("domain", extractDomain(event.getUser().getEmail()))
                .register(meterRegistry)
                .increment();
    }
    
    public Timer.Sample startUserCreationTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordUserCreationTime(Timer.Sample sample) {
        sample.stop(userCreationTimer);
    }
    
    private String extractDomain(String email) {
        return email.substring(email.indexOf('@') + 1);
    }
}
```

## Migration Considerations

### Preparing for Spring Boot 3.x Migration

```java
// Use constructor injection (already compatible)
@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// Avoid deprecated features
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    // This will need to be replaced in Spring Boot 3.x
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Configuration
    }
}

// Prepare for Jakarta namespace migration
// Document all javax.* imports for easier migration
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;
// These will become jakarta.* in Spring Boot 3.x
```

### Migration Checklist

1. **Java Version**: Ensure Java 17+ compatibility
2. **Dependencies**: Update all dependencies to Spring Boot 3.x compatible versions
3. **Namespace**: Replace javax.* with jakarta.*
4. **Security**: Replace WebSecurityConfigurerAdapter with SecurityFilterChain
5. **Testing**: Update test configurations and dependencies
6. **Actuator**: Review endpoint exposure settings
7. **Properties**: Update deprecated configuration properties

Spring Boot 2.x remains stable and supported for existing applications, but new projects should consider starting with Spring Boot 3.x for latest features and long-term support.
