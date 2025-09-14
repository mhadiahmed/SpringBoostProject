# Spring Security Guidelines

## Overview

Comprehensive guidelines for implementing Spring Security in modern applications, covering authentication, authorization, OAuth2, JWT tokens, and security testing best practices.

## Authentication Configuration

### Basic Security Configuration


```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> 
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .requestMatchers("/api/v1/public/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/products/**").hasAnyRole("USER", "ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/products/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}
```

### Custom User Details Service

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndActiveTrue(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                    "User not found with email: " + username));
        
        return UserPrincipal.create(user);
    }
    
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                    "User not found with id: " + id));
        
        return UserPrincipal.create(user);
    }
}
```

### User Principal Implementation

```java
@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    
    private Long id;
    private String email;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    private boolean enabled;
    
    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toList());
        
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                authorities,
                true, // accountNonExpired
                !user.isLocked(),
                true, // credentialsNonExpired
                user.isActive()
        );
    }
    
    @Override
    public String getUsername() {
        return email;
    }
}
```

## Authorization Patterns

### Method-Level Security

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    @PreAuthorize("hasRole('ADMIN') or @userService.isOwner(authentication.principal.id, #userId)")
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));
    }
    
    @PostAuthorize("hasRole('ADMIN') or returnObject.id == authentication.principal.id")
    public User updateUser(Long userId, UpdateUserRequest request) {
        User user = getUserById(userId);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        return userRepository.save(user);
    }
    
    @PreFilter("hasRole('ADMIN') or filterObject.userId == authentication.principal.id")
    public List<User> updateUsers(List<UpdateUserRequest> requests) {
        return requests.stream()
                .map(request -> updateUser(request.getUserId(), request))
                .collect(Collectors.toList());
    }
    
    public boolean isOwner(Long currentUserId, Long targetUserId) {
        return Objects.equals(currentUserId, targetUserId);
    }
}
```

### Custom Authorization Expressions

```java
@Component("authorizationService")
@RequiredArgsConstructor
public class AuthorizationService {
    
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    
    public boolean canAccessProject(Authentication authentication, Long projectId) {
        if (hasRole(authentication, "ADMIN")) {
            return true;
        }
        
        Long userId = getCurrentUserId(authentication);
        Project project = projectRepository.findById(projectId)
                .orElse(null);
        
        return project != null && (
                Objects.equals(project.getOwnerId(), userId) ||
                project.getMembers().stream()
                        .anyMatch(member -> Objects.equals(member.getUserId(), userId))
        );
    }
    
    public boolean canModifyUser(Authentication authentication, Long targetUserId) {
        return hasRole(authentication, "ADMIN") || 
               Objects.equals(getCurrentUserId(authentication), targetUserId);
    }
    
    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
    
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getId();
        }
        return null;
    }
}
```

### Usage in Controllers

```java
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {
    
    private final ProjectService projectService;
    
    @GetMapping("/{projectId}")
    @PreAuthorize("@authorizationService.canAccessProject(authentication, #projectId)")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable Long projectId) {
        Project project = projectService.getProjectById(projectId);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }
    
    @PutMapping("/{projectId}")
    @PreAuthorize("@authorizationService.canAccessProject(authentication, #projectId)")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        Project project = projectService.updateProject(projectId, request);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }
}
```

## JWT Token Handling

### JWT Utility Class

```java
@Component
@Slf4j
public class JwtTokenProvider {
    
    private final String jwtSecret;
    private final long jwtExpirationMs;
    private final long jwtRefreshExpirationMs;
    
    public JwtTokenProvider(@Value("${app.jwt.secret}") String jwtSecret,
                           @Value("${app.jwt.expiration-ms}") long jwtExpirationMs,
                           @Value("${app.jwt.refresh-expiration-ms}") long jwtRefreshExpirationMs) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
        this.jwtRefreshExpirationMs = jwtRefreshExpirationMs;
    }
    
    public String generateAccessToken(UserPrincipal userPrincipal) {
        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpirationMs);
        
        return Jwts.builder()
                .setSubject(Long.toString(userPrincipal.getId()))
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .claim("email", userPrincipal.getEmail())
                .claim("roles", userPrincipal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()))
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }
    
    public String generateRefreshToken(UserPrincipal userPrincipal) {
        Date expiryDate = new Date(System.currentTimeMillis() + jwtRefreshExpirationMs);
        
        return Jwts.builder()
                .setSubject(Long.toString(userPrincipal.getId()))
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }
    
    public Long getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        
        return Long.parseLong(claims.getSubject());
    }
    
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }
}
```

### JWT Authentication Filter

```java
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private CustomUserDetailsService customUserDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromJWT(jwt);
                
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

## OAuth2 Integration

### OAuth2 Resource Server Configuration

```java
@Configuration
@EnableWebSecurity
public class OAuth2ResourceServerConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/api/v1/public/**").permitAll()
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation("https://your-oauth-provider.com");
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = 
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");
        
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return jwtConverter;
    }
}
```

### OAuth2 Client Configuration

```java
@Configuration
@EnableOAuth2Client
public class OAuth2ClientConfig {
    
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(
                googleClientRegistration(),
                githubClientRegistration()
        );
    }
    
    private ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("${spring.security.oauth2.client.registration.google.client-id}")
                .clientSecret("${spring.security.oauth2.client.registration.google.client-secret}")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientName("Google")
                .build();
    }
    
    private ClientRegistration githubClientRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId("${spring.security.oauth2.client.registration.github.client-id}")
                .clientSecret("${spring.security.oauth2.client.registration.github.client-secret}")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
    }
}
```

## CORS Configuration

### CORS Configuration

```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",
                "http://localhost:4200",
                "https://*.yourdomain.com"
        ));
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With"
        ));
        configuration.setExposedHeaders(Arrays.asList("X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

## Security Testing

### Security Test Configuration

```java
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
}
```

### Controller Security Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityTestConfig.class)
class UserControllerSecurityTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyUserAccessToAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void shouldRequireAuthenticationForProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(username = "user@test.com", authorities = "ROLE_USER")
    void shouldAllowUserToAccessOwnProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile"))
                .andExpect(status().isOk());
    }
}
```

### Method Security Tests

```java
@SpringBootTest
@EnableMethodSecurity
class UserServiceSecurityTest {
    
    @Autowired
    private UserService userService;
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToGetAllUsers() {
        assertDoesNotThrow(() -> userService.getAllUsers());
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyUserFromGettingAllUsers() {
        assertThrows(AccessDeniedException.class, () -> userService.getAllUsers());
    }
    
    @Test
    @WithMockUser(username = "user@test.com", authorities = "ROLE_USER")
    void shouldAllowUserToUpdateOwnProfile() {
        // Given
        Long userId = 1L;
        UpdateUserRequest request = new UpdateUserRequest("John", "Doe");
        
        // Mock the current user
        when(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .thenReturn(UserPrincipal.builder().id(userId).build());
        
        // When & Then
        assertDoesNotThrow(() -> userService.updateUser(userId, request));
    }
}
```

## Security Best Practices

### Authentication Best Practices

1. **Password Security**:
   ```java
   @Bean
   public PasswordEncoder passwordEncoder() {
       // Use BCrypt with cost factor 12-14 for production
       return new BCryptPasswordEncoder(12);
   }
   ```

2. **Account Locking**:
   ```java
   @Service
   public class AccountLockingService {
       
       private static final int MAX_ATTEMPTS = 5;
       private static final long LOCK_TIME_DURATION = 24 * 60 * 60 * 1000; // 24 hours
       
       public void recordFailedAttempt(String email) {
           User user = userRepository.findByEmail(email).orElse(null);
           if (user != null) {
               int newFailAttempts = user.getFailedAttempt() + 1;
               user.setFailedAttempt(newFailAttempts);
               
               if (newFailAttempts >= MAX_ATTEMPTS) {
                   user.setAccountNonLocked(false);
                   user.setLockTime(new Date());
               }
               
               userRepository.save(user);
           }
       }
       
       public boolean unlockWhenTimeExpired(User user) {
           long lockTimeInMillis = user.getLockTime().getTime();
           long currentTimeInMillis = System.currentTimeMillis();
           
           if (lockTimeInMillis + LOCK_TIME_DURATION < currentTimeInMillis) {
               user.setAccountNonLocked(true);
               user.setLockTime(null);
               user.setFailedAttempt(0);
               userRepository.save(user);
               return true;
           }
           
           return false;
       }
   }
   ```

3. **Rate Limiting**:
   ```java
   @Component
   public class RateLimitingFilter extends OncePerRequestFilter {
       
       private final RateLimiter rateLimiter = RateLimiter.create(10.0); // 10 requests per second
       
       @Override
       protected void doFilterInternal(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     FilterChain filterChain) throws ServletException, IOException {
           
           if (!rateLimiter.tryAcquire()) {
               response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
               response.getWriter().write("Too many requests");
               return;
           }
           
           filterChain.doFilter(request, response);
       }
   }
   ```

### Authorization Best Practices

1. **Principle of Least Privilege**: Grant minimum necessary permissions
2. **Role-Based Access Control**: Use roles rather than individual permissions
3. **Resource-Based Authorization**: Check ownership for user-specific resources
4. **Method-Level Security**: Protect service methods, not just controllers

### Security Headers

```java
@Configuration
public class SecurityHeadersConfig {
    
    @Bean
    public SecurityFilterChain securityHeaders(HttpSecurity http) throws Exception {
        return http
                .headers(headers -> headers
                    .frameOptions().deny()
                    .contentTypeOptions().and()
                    .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                        .maxAgeInSeconds(31536000)
                        .includeSubdomains(true))
                    .cacheControl().and()
                    .and())
                .build();
    }
}
```

### Common Security Pitfalls to Avoid

1. **Don't store sensitive data in logs**
2. **Always validate input data**
3. **Use HTTPS in production**
4. **Don't expose stack traces to users**
5. **Implement proper session management**
6. **Keep dependencies updated**
7. **Use secure random number generation**
8. **Implement proper logout functionality**
9. **Validate JWT tokens properly**
10. **Don't trust client-side validation alone**
