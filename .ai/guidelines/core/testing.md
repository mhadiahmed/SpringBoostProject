# Spring Boot Testing Guidelines

## Overview

Comprehensive testing guidelines for Spring Boot applications, covering unit testing, integration testing, test slices, mocking strategies, and test configuration best practices.

## Testing Strategy

### Testing Pyramid

```
        /\
       /  \
      / UI \     (Few - End-to-End Tests)
     /______\
    /        \
   / Service  \   (Some - Integration Tests)
  /____________\
 /              \
/  Unit Tests    \  (Many - Fast, Isolated Tests)
/________________\
```

### Test Categories

1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test component interactions
3. **Slice Tests**: Test specific layers (Web, JPA, Security)
4. **End-to-End Tests**: Test complete user flows

## Unit Testing

### Service Layer Testing

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("User Service Tests")
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserService userService;
    
    @Captor
    private ArgumentCaptor<User> userCaptor;
    
    @Test
    @DisplayName("Should create user successfully when valid data provided")
    void shouldCreateUserSuccessfully() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("password123")
                .build();
        
        User savedUser = User.builder()
                .id(1L)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("encoded-password")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // When
        User result = userService.createUser(request);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.isActive()).isTrue();
        
        // Verify interactions
        verify(userRepository).existsByEmail("john@example.com");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(userCaptor.capture());
        verify(emailService).sendWelcomeEmail(savedUser);
        
        // Verify captured user
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(capturedUser.getPassword()).isEqualTo("encoded-password");
    }
    
    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailExists() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("existing@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("password123")
                .build();
        
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Email already exists: existing@example.com");
        
        // Verify no user was saved
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendWelcomeEmail(any(User.class));
    }
    
    @Test
    @DisplayName("Should handle email service failure gracefully")
    void shouldHandleEmailServiceFailureGracefully() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("password123")
                .build();
        
        User savedUser = User.builder()
                .id(1L)
                .email("john@example.com")
                .build();
        
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doThrow(new RuntimeException("Email service unavailable"))
                .when(emailService).sendWelcomeEmail(any(User.class));
        
        // When & Then - Should not throw exception
        assertThatCode(() -> userService.createUser(request))
                .doesNotThrowAnyException();
        
        // User should still be created
        verify(userRepository).save(any(User.class));
    }
    
    @ParameterizedTest
    @DisplayName("Should validate user creation with various inputs")
    @MethodSource("invalidUserData")
    void shouldValidateUserCreation(CreateUserRequest request, String expectedError) {
        // When & Then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(expectedError);
    }
    
    static Stream<Arguments> invalidUserData() {
        return Stream.of(
                Arguments.of(
                        CreateUserRequest.builder().email(null).build(),
                        "Email is required"
                ),
                Arguments.of(
                        CreateUserRequest.builder().email("invalid-email").build(),
                        "Invalid email format"
                ),
                Arguments.of(
                        CreateUserRequest.builder().email("test@example.com").password("123").build(),
                        "Password too short"
                )
        );
    }
    
    @Nested
    @DisplayName("User Update Tests")
    class UserUpdateTests {
        
        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            // Given
            Long userId = 1L;
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();
            
            User existingUser = User.builder()
                    .id(userId)
                    .email("john@example.com")
                    .firstName("John")
                    .lastName("Doe")
                    .build();
            
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            
            // When
            User result = userService.updateUser(userId, request);
            
            // Then
            assertThat(result.getFirstName()).isEqualTo("Jane");
            assertThat(result.getLastName()).isEqualTo("Smith");
            assertThat(result.getEmail()).isEqualTo("john@example.com"); // Should not change
        }
        
        @Test
        @DisplayName("Should throw exception when user not found")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            Long userId = 999L;
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Jane")
                    .build();
            
            when(userRepository.findById(userId)).thenReturn(Optional.empty());
            
            // When & Then
            assertThatThrownBy(() -> userService.updateUser(userId, request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("User not found with id: 999");
        }
    }
}
```

### Repository Layer Testing (Unit Tests)

```java
@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {
    
    @Mock
    private EntityManager entityManager;
    
    @Mock
    private CriteriaBuilder criteriaBuilder;
    
    @Mock
    private CriteriaQuery<User> criteriaQuery;
    
    @Mock
    private Root<User> root;
    
    @Mock
    private TypedQuery<User> typedQuery;
    
    @InjectMocks
    private UserRepositoryImpl userRepository;
    
    @Test
    @DisplayName("Should build correct criteria for user search")
    void shouldBuildCorrectCriteriaForUserSearch() {
        // Given
        UserSearchCriteria criteria = UserSearchCriteria.builder()
                .email("john@example.com")
                .minAge(18)
                .activeOnly(true)
                .build();
        
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createQuery(User.class)).thenReturn(criteriaQuery);
        when(criteriaQuery.from(User.class)).thenReturn(root);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(Collections.emptyList());
        
        // When
        userRepository.findUsersByComplexCriteria(criteria);
        
        // Then
        verify(criteriaBuilder).like(any(), eq("%john@example.com%"));
        verify(criteriaBuilder).greaterThanOrEqualTo(any(), eq(18));
        verify(criteriaBuilder).isTrue(any());
        verify(criteriaQuery).where(any(Predicate[].class));
    }
}
```

## Integration Testing

### Data JPA Testing

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("User Repository Integration Tests")
class UserRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    @DisplayName("Should find user by email")
    void shouldFindUserByEmail() {
        // Given
        User user = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .password("password")
                .active(true)
                .build();
        
        entityManager.persistAndFlush(user);
        
        // When
        Optional<User> found = userRepository.findByEmail("test@example.com");
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getFirstName()).isEqualTo("Test");
    }
    
    @Test
    @DisplayName("Should find users by last name containing")
    void shouldFindUsersByLastNameContaining() {
        // Given
        User user1 = createUser("john@example.com", "John", "Smith");
        User user2 = createUser("jane@example.com", "Jane", "Smithson");
        User user3 = createUser("bob@example.com", "Bob", "Johnson");
        
        entityManager.persistAndFlush(user1);
        entityManager.persistAndFlush(user2);
        entityManager.persistAndFlush(user3);
        
        // When
        List<User> users = userRepository.findByLastNameContainingIgnoreCase("smith");
        
        // Then
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getLastName)
                .containsExactlyInAnyOrder("Smith", "Smithson");
    }
    
    @Test
    @DisplayName("Should count active users")
    void shouldCountActiveUsers() {
        // Given
        createAndPersistUser("user1@example.com", true);
        createAndPersistUser("user2@example.com", true);
        createAndPersistUser("user3@example.com", false);
        
        // When
        long count = userRepository.countByActiveTrue();
        
        // Then
        assertThat(count).isEqualTo(2);
    }
    
    @Test
    @DisplayName("Should soft delete user")
    void shouldSoftDeleteUser() {
        // Given
        User user = createAndPersistUser("test@example.com", true);
        Long userId = user.getId();
        
        // When
        int updated = userRepository.softDeleteById(userId);
        entityManager.flush();
        entityManager.clear();
        
        // Then
        assertThat(updated).isEqualTo(1);
        
        User deletedUser = entityManager.find(User.class, userId);
        assertThat(deletedUser.isActive()).isFalse();
    }
    
    @Test
    @DisplayName("Should handle concurrent updates with optimistic locking")
    void shouldHandleConcurrentUpdatesWithOptimisticLocking() {
        // Given
        User user = createAndPersistUser("test@example.com", true);
        entityManager.flush();
        entityManager.clear();
        
        // When - Simulate concurrent update
        User user1 = userRepository.findById(user.getId()).orElseThrow();
        User user2 = userRepository.findById(user.getId()).orElseThrow();
        
        user1.setFirstName("Updated1");
        user2.setFirstName("Updated2");
        
        userRepository.save(user1);
        
        // Then
        assertThatThrownBy(() -> {
            userRepository.save(user2);
            entityManager.flush();
        }).isInstanceOf(OptimisticLockingFailureException.class);
    }
    
    private User createUser(String email, String firstName, String lastName) {
        return User.builder()
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .password("password")
                .active(true)
                .build();
    }
    
    private User createAndPersistUser(String email, boolean active) {
        User user = User.builder()
                .email(email)
                .firstName("Test")
                .lastName("User")
                .password("password")
                .active(active)
                .build();
        return entityManager.persistAndFlush(user);
    }
}
```

### Web Layer Testing

```java
@WebMvcTest(UserController.class)
@Import({SecurityTestConfig.class, TestConfig.class})
@ActiveProfiles("test")
@DisplayName("User Controller Tests")
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @DisplayName("Should create user successfully")
    @WithMockUser(roles = "ADMIN")
    void shouldCreateUserSuccessfully() throws Exception {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("password123")
                .build();
        
        User createdUser = User.builder()
                .id(1L)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(createdUser);
        
        // When & Then
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(header().exists("Location"))
                .andDo(print());
        
        verify(userService).createUser(any(CreateUserRequest.class));
    }
    
    @Test
    @DisplayName("Should return validation errors for invalid input")
    @WithMockUser(roles = "ADMIN")
    void shouldReturnValidationErrorsForInvalidInput() throws Exception {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .email("invalid-email")
                .firstName("")
                .lastName("D")
                .password("123")
                .build();
        
        // When & Then
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.firstName").exists())
                .andExpect(jsonPath("$.details.lastName").exists())
                .andExpect(jsonPath("$.details.password").exists())
                .andDo(print());
        
        verify(userService, never()).createUser(any(CreateUserRequest.class));
    }
    
    @Test
    @DisplayName("Should require authentication for protected endpoint")
    void shouldRequireAuthenticationForProtectedEndpoint() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isUnauthorized())
                .andDo(print());
        
        verify(userService, never()).getUserById(anyLong());
    }
    
    @Test
    @DisplayName("Should deny access for insufficient permissions")
    @WithMockUser(roles = "USER")
    void shouldDenyAccessForInsufficientPermissions() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andDo(print());
        
        verify(userService, never()).getAllUsers(any(Pageable.class));
    }
    
    @Test
    @DisplayName("Should handle service exceptions")
    @WithMockUser(roles = "ADMIN")
    void shouldHandleServiceExceptions() throws Exception {
        // Given
        when(userService.getUserById(999L))
                .thenThrow(new EntityNotFoundException("User", 999L));
        
        // When & Then
        mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with id: 999"))
                .andDo(print());
    }
    
    @ParameterizedTest
    @DisplayName("Should validate pagination parameters")
    @ValueSource(ints = {-1, 101})
    @WithMockUser(roles = "ADMIN")
    void shouldValidatePaginationParameters(int invalidSize) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users")
                        .param("size", String.valueOf(invalidSize)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }
    
    @Test
    @DisplayName("Should upload file successfully")
    @WithMockUser(username = "test@example.com", roles = "USER")
    void shouldUploadFileSuccessfully() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image content".getBytes()
        );
        
        User updatedUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .avatarUrl("http://example.com/avatar.jpg")
                .build();
        
        when(userService.updateUserAvatar(eq(1L), any(MultipartFile.class)))
                .thenReturn(updatedUser);
        
        // When & Then
        mockMvc.perform(multipart("/api/v1/users/1/avatar")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("http://example.com/avatar.jpg"))
                .andDo(print());
    }
}
```

### Security Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    @DisplayName("Should authenticate user with valid credentials")
    void shouldAuthenticateUserWithValidCredentials() {
        // Given
        createTestUser("test@example.com", "password123", "USER");
        
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();
        
        // When
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, AuthResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotEmpty();
        assertThat(response.getBody().getUser().getEmail()).isEqualTo("test@example.com");
    }
    
    @Test
    @DisplayName("Should reject invalid credentials")
    void shouldRejectInvalidCredentials() {
        // Given
        createTestUser("test@example.com", "password123", "USER");
        
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("wrongpassword")
                .build();
        
        // When
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, ErrorResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getError()).isEqualTo("AUTHENTICATION_FAILED");
    }
    
    @Test
    @DisplayName("Should protect admin endpoints from regular users")
    void shouldProtectAdminEndpointsFromRegularUsers() {
        // Given
        String token = createTokenForUser("user@example.com", "USER");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        // When
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/admin/users", HttpMethod.GET, entity, ErrorResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test
    @DisplayName("Should allow admin access to admin endpoints")
    void shouldAllowAdminAccessToAdminEndpoints() {
        // Given
        String token = createTokenForUser("admin@example.com", "ADMIN");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        // When
        ResponseEntity<PagedResponse> response = restTemplate.exchange(
                "/api/v1/admin/users", HttpMethod.GET, entity, PagedResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    private void createTestUser(String email, String password, String role) {
        User user = User.builder()
                .email(email)
                .firstName("Test")
                .lastName("User")
                .password(passwordEncoder.encode(password))
                .active(true)
                .build();
        
        Role userRole = Role.builder().name(role).build();
        user.addRole(userRole);
        
        userRepository.save(user);
    }
    
    private String createTokenForUser(String email, String role) {
        // Implementation to create JWT token for testing
        return "mock-jwt-token";
    }
}
```

## Test Configuration

### Test Configuration Classes

```java
@TestConfiguration
public class TestConfig {
    
    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(Instant.parse("2023-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
    
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public EmailService mockEmailService() {
        return Mockito.mock(EmailService.class);
    }
    
    @Bean
    public PasswordEncoder testPasswordEncoder() {
        // Use a simple encoder for tests
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "encoded_" + rawPassword;
            }
            
            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encodedPassword.equals("encoded_" + rawPassword);
            }
        };
    }
}

@TestConfiguration
public class SecurityTestConfig {
    
    @Bean
    @Primary
    public UserDetailsService testUserDetailsService() {
        UserDetails user = User.builder()
                .username("user@test.com")
                .password("{noop}password")
                .authorities("ROLE_USER")
                .build();
                
        UserDetails admin = User.builder()
                .username("admin@test.com")
                .password("{noop}password")
                .authorities("ROLE_ADMIN")
                .build();
                
        return new InMemoryUserDetailsManager(user, admin);
    }
    
    @Bean
    @Primary
    public JwtTokenProvider mockJwtTokenProvider() {
        return Mockito.mock(JwtTokenProvider.class);
    }
}
```

### Test Application Properties

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
    password:
    driver-class-name: org.h2.Driver
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
        format_sql: false
  
  security:
    oauth2:
      client:
        registration:
          test:
            client-id: test-client
            client-secret: test-secret

logging:
  level:
    com.company.project: DEBUG
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: ERROR
    org.hibernate.type.descriptor.sql.BasicBinder: ERROR

app:
  jwt:
    secret: test-secret-key-for-testing
    expiration-ms: 86400000
  email:
    enabled: false
```

### Test Utilities

```java
@Component
public class TestDataBuilder {
    
    public User.UserBuilder defaultUser() {
        return User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .password("password123")
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now());
    }
    
    public CreateUserRequest.CreateUserRequestBuilder defaultCreateUserRequest() {
        return CreateUserRequest.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .password("password123");
    }
    
    public User createUserWithRole(String email, String roleName) {
        User user = defaultUser()
                .email(email)
                .build();
        
        Role role = Role.builder().name(roleName).build();
        user.addRole(role);
        
        return user;
    }
}

@TestComponent
public class MockServerExtension implements BeforeEachCallback, AfterEachCallback {
    
    private WireMockServer wireMockServer;
    
    @Override
    public void beforeEach(ExtensionContext context) {
        wireMockServer = new WireMockServer(
                wireMockConfig()
                        .port(8089)
                        .notifier(new ConsoleNotifier(false))
        );
        wireMockServer.start();
        
        // Configure common stubs
        wireMockServer.stubFor(get(urlEqualTo("/external-api/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));
    }
    
    @Override
    public void afterEach(ExtensionContext context) {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
    
    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
```

### Custom Test Annotations

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public @interface IntegrationTest {
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(username = "test@example.com", roles = "USER")
public @interface WithMockRegularUser {
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(username = "admin@example.com", roles = "ADMIN")
public @interface WithMockAdminUser {
}

// Usage
@IntegrationTest
class UserServiceIntegrationTest {
    
    @Test
    @WithMockRegularUser
    void shouldAllowUserToAccessOwnProfile() {
        // Test implementation
    }
    
    @Test
    @WithMockAdminUser
    void shouldAllowAdminToAccessAllUsers() {
        // Test implementation
    }
}
```

## Performance Testing

### Load Testing with JMeter Integration

```java
@Test
@DisplayName("Performance Test - User Creation")
void performanceTestUserCreation() {
    int numberOfUsers = 100;
    int threadPoolSize = 10;
    
    ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
    CountDownLatch latch = new CountDownLatch(numberOfUsers);
    
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);
    
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < numberOfUsers; i++) {
        final int userIndex = i;
        executor.submit(() -> {
            try {
                CreateUserRequest request = CreateUserRequest.builder()
                        .email("user" + userIndex + "@example.com")
                        .firstName("User")
                        .lastName("" + userIndex)
                        .password("password123")
                        .build();
                
                userService.createUser(request);
                successCount.incrementAndGet();
                
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Failed to create user {}: {}", userIndex, e.getMessage());
            } finally {
                latch.countDown();
            }
        });
    }
    
    try {
        latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    
    long endTime = System.currentTimeMillis();
    long totalTime = endTime - startTime;
    
    executor.shutdown();
    
    log.info("Performance Test Results:");
    log.info("Total time: {} ms", totalTime);
    log.info("Successful operations: {}", successCount.get());
    log.info("Failed operations: {}", failureCount.get());
    log.info("Operations per second: {}", (numberOfUsers * 1000.0) / totalTime);
    
    assertThat(successCount.get()).isGreaterThan(numberOfUsers * 0.95); // 95% success rate
    assertThat(totalTime).isLessThan(10000); // Complete within 10 seconds
}
```

## Test Best Practices

### Testing Principles

1. **Fast**: Tests should run quickly
2. **Independent**: Tests should not depend on each other
3. **Repeatable**: Tests should produce consistent results
4. **Self-Validating**: Tests should clearly pass or fail
5. **Timely**: Tests should be written close to production code

### Naming Conventions

```java
// Good test method names
@Test
void shouldCreateUserSuccessfullyWhenValidDataProvided() { }

@Test
void shouldThrowValidationExceptionWhenEmailIsInvalid() { }

@Test
void shouldReturnEmptyListWhenNoUsersExist() { }

// Test class organization
class UserServiceTest {
    
    @Nested
    @DisplayName("User Creation")
    class UserCreation {
        // Creation tests
    }
    
    @Nested
    @DisplayName("User Update")
    class UserUpdate {
        // Update tests
    }
    
    @Nested
    @DisplayName("User Deletion")
    class UserDeletion {
        // Deletion tests
    }
}
```

### Assertion Best Practices

```java
@Test
void shouldCreateUserWithCorrectProperties() {
    // Given
    CreateUserRequest request = CreateUserRequest.builder()
            .email("test@example.com")
            .firstName("John")
            .lastName("Doe")
            .build();
    
    // When
    User result = userService.createUser(request);
    
    // Then - Use specific assertions
    assertThat(result)
            .isNotNull()
            .satisfies(user -> {
                assertThat(user.getId()).isNotNull();
                assertThat(user.getEmail()).isEqualTo("test@example.com");
                assertThat(user.getFirstName()).isEqualTo("John");
                assertThat(user.getLastName()).isEqualTo("Doe");
                assertThat(user.isActive()).isTrue();
                assertThat(user.getCreatedAt()).isNotNull();
            });
    
    // Verify collections
    assertThat(result.getRoles())
            .isNotEmpty()
            .hasSize(1)
            .extracting(Role::getName)
            .containsExactly("USER");
}
```

### Mock Usage Guidelines

```java
@Test
void shouldUseStubsForQueryOperations() {
    // Use stubs for queries
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    
    // Use verification for commands
    verify(emailService).sendWelcomeEmail(user);
    verify(userRepository).save(any(User.class));
}

@Test
void shouldAvoidOverMocking() {
    // Don't mock value objects
    Address address = new Address("123 Main St", "City", "State", "12345", "Country");
    
    // Don't mock simple data structures
    List<String> roles = Arrays.asList("USER", "ADMIN");
    
    // Mock only external dependencies
    when(externalApiService.validateEmail(anyString())).thenReturn(true);
}
```

## Testing Summary

### DO:
- Write tests first (TDD approach)
- Use appropriate test slice annotations
- Test behavior, not implementation
- Use meaningful test data
- Verify interactions with mocks
- Use TestContainers for integration tests
- Organize tests with @Nested classes
- Use parameterized tests for multiple scenarios

### DON'T:
- Test private methods directly
- Use @SpringBootTest for everything
- Mock value objects or simple data structures
- Write flaky tests
- Ignore test failures
- Use production data in tests
- Write tests that depend on external services
- Skip edge cases and error scenarios

### Test Coverage Goals:
- Unit tests: 80%+ coverage
- Integration tests: Critical paths covered
- End-to-end tests: Main user journeys
- Performance tests: Critical operations
- Security tests: Authentication/authorization flows
