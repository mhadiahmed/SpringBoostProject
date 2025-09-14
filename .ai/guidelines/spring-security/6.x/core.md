# Spring Security 6.x Specific Guidelines

## Overview

Spring Security 6.x guidelines for SecurityFilterChain-based configuration, lambda DSL, and Jakarta EE namespace migration. This version is used with Spring Boot 3.x applications.

## SecurityFilterChain Configuration

### Basic Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {
    
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    
    public SecurityConfig(UserDetailsService userDetailsService,
                         PasswordEncoder passwordEncoder,
                         JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/products").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}
```

### Lambda DSL Configuration

```java
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {
    
    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .build();
    }
    
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/login", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard")
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .build();
    }
}
```

## Method-Level Security

### @EnableMethodSecurity Configuration

```java
@Configuration
@EnableMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true
)
public class MethodSecurityConfig {
    
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = 
                new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(customPermissionEvaluator());
        return expressionHandler;
    }
    
    @Bean
    public PermissionEvaluator customPermissionEvaluator() {
        return new CustomPermissionEvaluator();
    }
}

@Service
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
    
    @PostAuthorize("returnObject.email == authentication.name or hasRole('ADMIN')")
    public User updateUser(Long userId, UpdateUserRequest request) {
        User user = getUserById(userId);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        return userRepository.save(user);
    }
    
    @PreFilter("filterObject.userId == authentication.principal.id or hasRole('ADMIN')")
    public List<User> updateUsers(List<UpdateUserRequest> requests) {
        return requests.stream()
                .map(request -> updateUser(request.getUserId(), request))
                .toList();
    }
    
    @PostFilter("filterObject.createdBy == authentication.name or hasRole('ADMIN')")
    public List<User> getUsersByDepartment(String department) {
        return userRepository.findByDepartment(department);
    }
    
    // JSR-250 annotations
    @RolesAllowed({"ADMIN", "MANAGER"})
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
    
    @PermitAll
    public User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
    }
    
    // Custom permission evaluation
    @PreAuthorize("hasPermission(#userId, 'User', 'read')")
    public User getUserWithPermissionCheck(Long userId) {
        return getUserById(userId);
    }
}
```

## OAuth2 Resource Server Configuration

### JWT-based Resource Server

```java
@Configuration
public class OAuth2ResourceServerConfig {
    
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;
    
    @Bean
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);
        jwtDecoder.setJwtValidator(jwtValidator());
        return jwtDecoder;
    }
    
    @Bean
    public Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        converter.setPrincipalClaimName("sub");
        return converter;
    }
    
    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        return jwt -> {
            // Extract roles from JWT claims
            Collection<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                roles = List.of();
            }
            
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
        };
    }
    
    @Bean
    public OAuth2TokenValidator<Jwt> jwtValidator() {
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuerUri),
                new JwtAudienceValidator("my-app"),
                customJwtValidator()
        );
        
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
    
    @Bean
    public OAuth2TokenValidator<Jwt> customJwtValidator() {
        return new OAuth2TokenValidator<Jwt>() {
            @Override
            public OAuth2TokenValidatorResult validate(Jwt jwt) {
                // Custom validation logic
                if (jwt.getClaimAsString("custom_claim") == null) {
                    return OAuth2TokenValidatorResult.failure("Missing custom claim");
                }
                return OAuth2TokenValidatorResult.success();
            }
        };
    }
}
```

### Opaque Token Configuration

```java
@Configuration
public class OpaqueTokenResourceServerConfig {
    
    @Value("${spring.security.oauth2.resourceserver.opaque-token.introspection-uri}")
    private String introspectionUri;
    
    @Value("${spring.security.oauth2.resourceserver.opaque-token.client-id}")
    private String clientId;
    
    @Value("${spring.security.oauth2.resourceserver.opaque-token.client-secret}")
    private String clientSecret;
    
    @Bean
    public SecurityFilterChain opaqueTokenFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .opaqueToken(opaque -> opaque
                    .introspectionUri(introspectionUri)
                    .introspectionClientCredentials(clientId, clientSecret)
                    .introspector(opaqueTokenIntrospector())
                )
            )
            .build();
    }
    
    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector() {
        SpringOpaqueTokenIntrospector introspector = 
                new SpringOpaqueTokenIntrospector(introspectionUri, clientId, clientSecret);
        
        introspector.setRequestEntityConverter(requestEntityConverter());
        return introspector;
    }
    
    @Bean
    public Converter<String, RequestEntity<?>> requestEntityConverter() {
        return token -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);
            body.add("token_type_hint", "access_token");
            
            return new RequestEntity<>(body, headers, HttpMethod.POST, URI.create(introspectionUri));
        };
    }
}
```

## OAuth2 Client Configuration

### Authorization Code Flow

```java
@Configuration
public class OAuth2ClientConfig {
    
    @Bean
    public SecurityFilterChain oauth2ClientFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/login/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard")
                .failureUrl("/login?error=true")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oauth2UserService())
                    .oidcUserService(oidcUserService())
                )
                .successHandler(oauth2AuthenticationSuccessHandler())
                .failureHandler(oauth2AuthenticationFailureHandler())
            )
            .oauth2Client(oauth2 -> oauth2
                .authorizationCodeGrant(authCode -> authCode
                    .authorizationRequestResolver(authorizationRequestResolver())
                    .accessTokenResponseClient(accessTokenResponseClient())
                )
            )
            .build();
    }
    
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        
        return userRequest -> {
            OAuth2User oauth2User = delegate.loadUser(userRequest);
            
            // Process the OAuth2User - save to database, map roles, etc.
            return processOAuth2User(userRequest, oauth2User);
        };
    }
    
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        
        return userRequest -> {
            OidcUser oidcUser = delegate.loadUser(userRequest);
            
            // Process the OidcUser
            return processOidcUser(userRequest, oidcUser);
        };
    }
    
    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        
        DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        
        authorizationRequestResolver.setAuthorizationRequestCustomizer(
                authorizationRequestCustomizer());
        
        return authorizationRequestResolver;
    }
    
    @Bean
    public Consumer<OAuth2AuthorizationRequest.Builder> authorizationRequestCustomizer() {
        return customizer -> customizer
                .additionalParameters(params -> params.put("custom_param", "value"))
                .attributes(attrs -> attrs.put("custom_attr", "value"));
    }
    
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
        DefaultAuthorizationCodeTokenResponseClient accessTokenResponseClient =
                new DefaultAuthorizationCodeTokenResponseClient();
        
        accessTokenResponseClient.setRequestEntityConverter(tokenRequestEntityConverter());
        accessTokenResponseClient.setRestOperations(restTemplate());
        
        return accessTokenResponseClient;
    }
    
    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        // Custom processing logic
        return oauth2User;
    }
    
    private OidcUser processOidcUser(OidcUserRequest userRequest, OidcUser oidcUser) {
        // Custom processing logic
        return oidcUser;
    }
}
```

### WebClient with OAuth2

```java
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        
        return WebClient.builder()
                .apply(oauth2Client.oauth2Configuration())
                .build();
    }
    
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {
        
        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken()
                        .clientCredentials()
                        .password()
                        .build();
        
        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientRepository);
        
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        
        // Add contextual parameters
        authorizedClientManager.setContextAttributesMapper(contextAttributesMapper());
        
        return authorizedClientManager;
    }
    
    @Bean
    public Function<OAuth2AuthorizeRequest, Map<String, Object>> contextAttributesMapper() {
        return authorizeRequest -> {
            Map<String, Object> contextAttributes = new HashMap<>();
            
            HttpServletRequest servletRequest = authorizeRequest.getAttribute(
                    HttpServletRequest.class.getName());
            
            if (servletRequest != null) {
                contextAttributes.put(HttpServletRequest.class.getName(), servletRequest);
                contextAttributes.put(HttpServletResponse.class.getName(),
                        authorizeRequest.getAttribute(HttpServletResponse.class.getName()));
            }
            
            return contextAttributes;
        };
    }
}

@Service
public class ExternalApiService {
    
    private final WebClient webClient;
    
    public ExternalApiService(WebClient webClient) {
        this.webClient = webClient;
    }
    
    public Mono<String> callExternalApi() {
        return webClient
                .get()
                .uri("https://api.example.com/data")
                .attributes(oauth2AuthorizedClient("my-client"))
                .retrieve()
                .bodyToMono(String.class);
    }
    
    public Mono<String> callApiWithClientCredentials() {
        return webClient
                .get()
                .uri("https://api.example.com/data")
                .attributes(clientRegistrationId("client-credentials"))
                .retrieve()
                .bodyToMono(String.class);
    }
}
```

## Custom Authentication

### Custom UserDetailsService

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        
        return UserPrincipal.create(user);
    }
    
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        
        return UserPrincipal.create(user);
    }
}

public class UserPrincipal implements UserDetails, OAuth2User {
    
    private final Long id;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private Map<String, Object> attributes;
    
    public UserPrincipal(Long id, String email, String password, 
                        Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }
    
    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toList());
        
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                authorities
        );
    }
    
    public static UserPrincipal create(User user, Map<String, Object> attributes) {
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        userPrincipal.setAttributes(attributes);
        return userPrincipal;
    }
    
    // UserDetails implementation
    @Override
    public String getUsername() {
        return email;
    }
    
    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    // OAuth2User implementation
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public String getName() {
        return String.valueOf(id);
    }
    
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public String getEmail() {
        return email;
    }
}
```

## Password Encoding

### Modern Password Encoder Configuration

```java
@Configuration
public class PasswordConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Use Argon2 for new applications (Spring Security 6.x recommendation)
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
    
    @Bean
    public PasswordEncoder delegatingPasswordEncoder() {
        String encodingId = "argon2";
        Map<String, PasswordEncoder> encoders = Map.of(
                encodingId, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "bcrypt", new BCryptPasswordEncoder(),
                "pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                "scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()
        );
        
        DelegatingPasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(encodingId, encoders);
        passwordEncoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        
        return passwordEncoder;
    }
}
```

## CSRF Protection

### CSRF Configuration with Lambda DSL

```java
@Configuration
public class CsrfConfig {
    
    @Bean
    public SecurityFilterChain csrfFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/v1/webhooks/**", "/api/v1/public/**")
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
            )
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .build();
    }
}

@RestController
public class CsrfController {
    
    @GetMapping("/api/v1/csrf")
    public CsrfToken csrfToken(CsrfToken token) {
        return token;
    }
}
```

## Testing Security

### Security Test Configuration

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class UserControllerSecurityTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyUserToAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    void shouldRequireAuthenticationForProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithJwt(claims = @Claim(name = "roles", value = "[\"ADMIN\"]"))
    void shouldAllowJwtAdminAccess() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }
    
    @Test
    @WithOAuth2Login(authorities = {"ROLE_USER"})
    void shouldAllowOAuth2UserAccess() throws Exception {
        mockMvc.perform(get("/api/v1/user/profile"))
                .andExpect(status().isOk());
    }
}

@TestConfiguration
public class TestSecurityConfig {
    
    @Bean
    @Primary
    public UserDetailsService testUserDetailsService() {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("password"))
                .roles("ADMIN")
                .build();
        
        UserDetails user = User.builder()
                .username("user")
                .password(passwordEncoder().encode("password"))
                .roles("USER")
                .build();
        
        return new InMemoryUserDetailsManager(admin, user);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

## Observability and Monitoring

### Security Events

```java
@Component
public class SecurityEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityEventListener.class);
    
    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        logger.info("Authentication successful for user: {}", authentication.getName());
        
        // Custom logic - audit logging, metrics, etc.
    }
    
    @EventListener
    public void handleAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        Authentication authentication = event.getAuthentication();
        Exception exception = event.getException();
        
        logger.warn("Authentication failed for user: {} - {}", 
                authentication.getName(), exception.getMessage());
        
        // Custom logic - security monitoring, rate limiting, etc.
    }
    
    @EventListener
    public void handleAuthorizationFailure(AuthorizationDeniedEvent event) {
        Authentication authentication = event.getAuthentication();
        AuthorizationDecision decision = event.getAuthorizationDecision();
        
        logger.warn("Authorization denied for user: {} - {}", 
                authentication.getName(), decision);
    }
}
```

## Migration from Spring Security 5.x

### Key Changes

```java
// Spring Security 5.x (Deprecated)
@Configuration
@EnableWebSecurity
public class OldSecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated();
    }
}

// Spring Security 6.x (Recommended)
@Configuration
@EnableWebSecurity
public class NewSecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .build();
    }
}

// Replace antMatchers with requestMatchers
// Replace authorizeRequests with authorizeHttpRequests
// Use lambda DSL instead of chaining methods
// Replace WebSecurityConfigurerAdapter with SecurityFilterChain beans
```

Spring Security 6.x provides enhanced security with lambda DSL configuration, improved OAuth2 support, and better integration with modern authentication patterns. The Jakarta EE namespace migration ensures compatibility with Spring Boot 3.x applications.
