# Spring Web Guidelines

## Overview

Comprehensive guidelines for Spring Web development, covering REST API design, exception handling, validation patterns, Swagger/OpenAPI integration, and CORS configuration.

## REST API Design

### Controller Best Practices

```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "User Management", description = "Operations related to user management")
public class UserController {
    
    private final UserService userService;
    
    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve paginated list of users")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        log.info("Fetching users - page: {}, size: {}, sortBy: {}, sortDir: {}", 
                page, size, sortBy, sortDir);
        
        Pageable pageable = PageRequest.of(page, size, 
                Sort.Direction.fromString(sortDir), sortBy);
        
        Page<User> users = userService.getAllUsers(pageable);
        PagedResponse<UserResponse> response = PagedResponse.of(
                users.map(UserResponse::from));
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve a specific user by their ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User found"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN') or @userService.isCurrentUser(authentication, #id)")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable @Positive Long id) {
        
        log.info("Fetching user with id: {}", id);
        User user = userService.getUserById(id);
        return ResponseEntity.ok(UserResponse.from(user));
    }
    
    @PostMapping
    @Operation(summary = "Create new user", description = "Create a new user account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        
        log.info("Creating new user with email: {}", request.getEmail());
        User user = userService.createUser(request);
        UserResponse response = UserResponse.from(user);
        
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();
        
        return ResponseEntity.created(location).body(response);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update an existing user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN') or @userService.isCurrentUser(authentication, #id)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        
        log.info("Updating user with id: {}", id);
        User user = userService.updateUser(id, request);
        return ResponseEntity.ok(UserResponse.from(user));
    }
    
    @PatchMapping("/{id}")
    @Operation(summary = "Partially update user", description = "Update specific fields of a user")
    public ResponseEntity<UserResponse> patchUser(
            @PathVariable @Positive Long id,
            @RequestBody Map<String, Object> updates) {
        
        log.info("Patching user with id: {} with updates: {}", id, updates.keySet());
        User user = userService.patchUser(id, updates);
        return ResponseEntity.ok(UserResponse.from(user));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete a user account")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable @Positive Long id) {
        log.info("Deleting user with id: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/orders")
    @Operation(summary = "Get user orders", description = "Retrieve orders for a specific user")
    @PreAuthorize("hasRole('ADMIN') or @userService.isCurrentUser(authentication, #id)")
    public ResponseEntity<PagedResponse<OrderResponse>> getUserOrders(
            @PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("orderDate").descending());
        Page<Order> orders = userService.getUserOrders(id, pageable);
        PagedResponse<OrderResponse> response = PagedResponse.of(
                orders.map(OrderResponse::from));
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/avatar")
    @Operation(summary = "Upload user avatar", description = "Upload avatar image for user")
    @PreAuthorize("hasRole('ADMIN') or @userService.isCurrentUser(authentication, #id)")
    public ResponseEntity<UserResponse> uploadAvatar(
            @PathVariable @Positive Long id,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            throw new ValidationException("File cannot be empty");
        }
        
        if (!isValidImageFile(file)) {
            throw new ValidationException("Only image files are allowed");
        }
        
        log.info("Uploading avatar for user: {}, file: {}", id, file.getOriginalFilename());
        User user = userService.updateUserAvatar(id, file);
        return ResponseEntity.ok(UserResponse.from(user));
    }
    
    private boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }
}
```

### Request/Response DTOs

```java
// Request DTOs
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    
    @Valid
    private AddressRequest address;
    
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be valid")
    private String phone;
    
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {
    
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;
    
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;
    
    @Valid
    private AddressRequest address;
    
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be valid")
    private String phone;
    
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {
    
    @NotBlank(message = "Street address is required")
    @Size(max = 100, message = "Street address must not exceed 100 characters")
    private String streetAddress;
    
    @NotBlank(message = "City is required")
    @Size(max = 50, message = "City must not exceed 50 characters")
    private String city;
    
    @NotBlank(message = "State is required")
    @Size(max = 50, message = "State must not exceed 50 characters")
    private String state;
    
    @NotBlank(message = "Postal code is required")
    @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "Postal code must be valid")
    private String postalCode;
    
    @NotBlank(message = "Country is required")
    @Size(max = 50, message = "Country must not exceed 50 characters")
    private String country;
}

// Response DTOs
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private LocalDate dateOfBirth;
    private AddressResponse address;
    private String avatarUrl;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> roles;
    
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .dateOfBirth(user.getDateOfBirth())
                .address(user.getAddress() != null ? AddressResponse.from(user.getAddress()) : null)
                .avatarUrl(user.getAvatarUrl())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .build();
    }
}

@Data
@Builder
public class AddressResponse {
    private String streetAddress;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    public static AddressResponse from(Address address) {
        return AddressResponse.builder()
                .streetAddress(address.getStreetAddress())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }
}

// Paginated Response Wrapper
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean hasNext;
    private boolean hasPrevious;
    
    public static <T> PagedResponse<T> of(Page<T> page) {
        return PagedResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
```

### Exception Handling

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage()));
        
        log.warn("Validation errors: {}", errors);
        
        return ErrorResponse.builder()
                .error("VALIDATION_ERROR")
                .message("Validation failed")
                .details(errors)
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        
        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(propertyPath, message);
        });
        
        log.warn("Constraint violation errors: {}", errors);
        
        return ErrorResponse.builder()
                .error("CONSTRAINT_VIOLATION")
                .message("Constraint validation failed")
                .details(errors)
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        
        return ErrorResponse.builder()
                .error("ENTITY_NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        
        return ErrorResponse.builder()
                .error("ACCESS_DENIED")
                .message("Access denied")
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        
        return ErrorResponse.builder()
                .error("AUTHENTICATION_FAILED")
                .message("Authentication required")
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        
        String message = "Data conflict occurred";
        if (ex.getCause() instanceof ConstraintViolationException) {
            message = "Duplicate entry or constraint violation";
        }
        
        return ErrorResponse.builder()
                .error("DATA_CONFLICT")
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        
        return ErrorResponse.builder()
                .error("METHOD_NOT_ALLOWED")
                .message("HTTP method not supported for this endpoint")
                .details(Map.of("supportedMethods", Arrays.toString(ex.getSupportedMethods())))
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getMessage());
        
        return ErrorResponse.builder()
                .error("UNSUPPORTED_MEDIA_TYPE")
                .message("Media type not supported")
                .details(Map.of("supportedMediaTypes", ex.getSupportedMediaTypes().toString()))
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ErrorResponse handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("File size exceeded: {}", ex.getMessage());
        
        return ErrorResponse.builder()
                .error("FILE_SIZE_EXCEEDED")
                .message("File size exceeds maximum allowed limit")
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unexpected error [{}] for request: {} {}", 
                requestId, request.getMethod(), request.getRequestURI(), ex);
        
        return ErrorResponse.builder()
                .error("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .details(Map.of("requestId", requestId))
                .timestamp(Instant.now())
                .build();
    }
    
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleAsyncRequestTimeout(AsyncRequestTimeoutException ex) {
        log.warn("Async request timeout: {}", ex.getMessage());
        
        return ErrorResponse.builder()
                .error("REQUEST_TIMEOUT")
                .message("Request processing timeout")
                .timestamp(Instant.now())
                .build();
    }
}

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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

### Validation Patterns

```java
// Custom Validation Annotations
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchesValidator.class)
@Documented
public @interface PasswordMatches {
    String message() default "Passwords don't match";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, Object> {
    
    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        if (obj instanceof CreateUserRequest) {
            CreateUserRequest user = (CreateUserRequest) obj;
            return user.getPassword() != null && user.getPassword().equals(user.getConfirmPassword());
        }
        return true;
    }
}

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
@Documented
public @interface UniqueEmail {
    String message() default "Email already exists";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

@Component
public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email != null && !userRepository.existsByEmail(email);
    }
}

// Validation Groups
public interface CreateValidation {}
public interface UpdateValidation {}

@Data
@PasswordMatches(groups = CreateValidation.class)
public class CreateUserRequest {
    
    @NotBlank(groups = {CreateValidation.class, UpdateValidation.class})
    @Email(groups = {CreateValidation.class, UpdateValidation.class})
    @UniqueEmail(groups = CreateValidation.class)
    private String email;
    
    @NotBlank(groups = CreateValidation.class)
    @Size(min = 8, groups = {CreateValidation.class, UpdateValidation.class})
    private String password;
    
    @NotBlank(groups = CreateValidation.class)
    private String confirmPassword;
}

// Controller with validation groups
@PostMapping
public ResponseEntity<UserResponse> createUser(
        @Validated(CreateValidation.class) @RequestBody CreateUserRequest request) {
    // Implementation
}

@PutMapping("/{id}")
public ResponseEntity<UserResponse> updateUser(
        @PathVariable Long id,
        @Validated(UpdateValidation.class) @RequestBody UpdateUserRequest request) {
    // Implementation
}
```

### OpenAPI/Swagger Integration

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "User Management API",
        version = "1.0",
        description = "API for managing users and related operations",
        contact = @Contact(
            name = "API Support",
            email = "support@company.com",
            url = "https://company.com/support"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development server"),
        @Server(url = "https://api.company.com", description = "Production server")
    },
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer"
)
public class OpenApiConfig {
    
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/public/**")
                .build();
    }
    
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }
    
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("users")
                .pathsToMatch("/api/v1/users/**")
                .build();
    }
}

// Enhanced controller documentation
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management endpoints")
public class UserController {
    
    @Operation(
        summary = "Create a new user",
        description = "Creates a new user account with the provided information"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    name = "Validation Error",
                    value = """
                    {
                        "error": "VALIDATION_ERROR",
                        "message": "Validation failed",
                        "details": {
                            "email": "Email is required",
                            "password": "Password must be at least 8 characters"
                        },
                        "timestamp": "2023-01-01T00:00:00Z"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Email already exists",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Parameter(description = "User creation data", required = true)
            @Valid @RequestBody CreateUserRequest request) {
        // Implementation
    }
}
```

### CORS Configuration

```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",      // React dev server
                "http://localhost:4200",      // Angular dev server
                "https://*.company.com",      // Production domains
                "https://*.company-dev.com"   // Development domains
        ));
        
        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        
        // Exposed headers
        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                "X-Total-Count",
                "X-Page-Number",
                "X-Page-Size"
        ));
        
        // Allow credentials
        configuration.setAllowCredentials(true);
        
        // Preflight cache duration
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
    
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("*")
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
```

### Content Negotiation

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(true)
                .parameterName("format")
                .defaultContentType(MediaType.APPLICATION_JSON)
                .mediaType("json", MediaType.APPLICATION_JSON)
                .mediaType("xml", MediaType.APPLICATION_XML)
                .mediaType("csv", MediaType.parseMediaType("text/csv"))
                .ignoreAcceptHeader(false);
    }
    
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToLocalDateConverter());
        registry.addConverter(new StringToEnumConverter());
    }
    
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Add custom message converters
        converters.add(new CsvHttpMessageConverter());
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper()));
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}

// Custom converters
public class StringToLocalDateConverter implements Converter<String, LocalDate> {
    @Override
    public LocalDate convert(String source) {
        return LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE);
    }
}

@Component
public class StringToEnumConverter implements ConverterFactory<String, Enum> {
    
    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return new StringToEnum<>(targetType);
    }
    
    private static class StringToEnum<T extends Enum> implements Converter<String, T> {
        private final Class<T> enumType;
        
        public StringToEnum(Class<T> enumType) {
            this.enumType = enumType;
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public T convert(String source) {
            return (T) Enum.valueOf(enumType, source.toUpperCase());
        }
    }
}
```

### Async Processing

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean
    public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    
    private final ReportService reportService;
    
    @PostMapping("/generate")
    @Operation(summary = "Generate report asynchronously")
    public ResponseEntity<Map<String, String>> generateReport(
            @Valid @RequestBody ReportRequest request) {
        
        String taskId = UUID.randomUUID().toString();
        reportService.generateReportAsync(taskId, request);
        
        return ResponseEntity.accepted()
                .body(Map.of(
                    "taskId", taskId,
                    "status", "PROCESSING",
                    "message", "Report generation started"
                ));
    }
    
    @GetMapping("/status/{taskId}")
    @Operation(summary = "Check report generation status")
    public ResponseEntity<ReportStatusResponse> getReportStatus(
            @PathVariable String taskId) {
        
        ReportStatus status = reportService.getReportStatus(taskId);
        return ResponseEntity.ok(ReportStatusResponse.from(status));
    }
}

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {
    
    @Async("taskExecutor")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<String> generateReportAsync(String taskId, ReportRequest request) {
        log.info("Starting report generation for task: {}", taskId);
        
        try {
            // Simulate long-running task
            Thread.sleep(5000);
            
            String reportUrl = generateReport(request);
            updateReportStatus(taskId, ReportStatus.COMPLETED, reportUrl);
            
            return CompletableFuture.completedFuture(reportUrl);
            
        } catch (Exception e) {
            log.error("Report generation failed for task: {}", taskId, e);
            updateReportStatus(taskId, ReportStatus.FAILED, null);
            throw e;
        }
    }
}
```

## Best Practices Summary

### Controller Design
- Use specific HTTP status codes
- Implement proper pagination
- Use DTOs for request/response
- Add comprehensive validation
- Include proper API documentation
- Handle file uploads securely

### Exception Handling
- Use global exception handlers
- Provide meaningful error messages
- Include appropriate HTTP status codes
- Log errors appropriately
- Don't expose sensitive information

### Validation
- Use Bean Validation annotations
- Create custom validators when needed
- Use validation groups for different scenarios
- Validate at controller level
- Provide clear validation messages

### API Documentation
- Use OpenAPI/Swagger annotations
- Provide examples and descriptions
- Document all possible responses
- Include authentication requirements
- Group endpoints logically

### Security
- Implement proper CORS configuration
- Use method-level security
- Validate all inputs
- Handle authentication/authorization
- Protect against common vulnerabilities

### Performance
- Use async processing for long operations
- Implement proper caching
- Use appropriate fetch strategies
- Monitor performance metrics
- Optimize database queries
