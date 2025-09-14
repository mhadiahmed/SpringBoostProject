# Spring Cloud 2023.x Specific Guidelines

## Overview

Spring Cloud 2023.x guidelines for next-generation microservices architecture with enhanced cloud-native features, improved observability, and modern patterns. This version builds upon Spring Boot 3.x and includes significant improvements in performance and developer experience.

## Enhanced Service Discovery

### Spring Cloud Discovery with Consul (Latest)

```java
// Enhanced dependencies in pom.xml
/*
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-consul-discovery</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-consul-config</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing</artifactId>
</dependency>
*/

@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

// Enhanced configuration with modern patterns
# application.yml
spring:
  application:
    name: user-service
  cloud:
    consul:
      host: localhost
      port: 8500
      discovery:
        enabled: true
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-timeout: 5s
        health-check-critical-timeout: 30s
        instance-id: ${spring.application.name}:${spring.cloud.client.hostname}:${server.port}
        prefer-ip-address: true
        ip-address: ${spring.cloud.client.ip-address:localhost}
        tags:
          - version=2.0.0
          - environment=${spring.profiles.active:dev}
          - zone=${AVAILABILITY_ZONE:zone-1}
        metadata:
          version: "2.0.0"
          team: "backend"
          buildNumber: "${BUILD_NUMBER:local}"
          gitCommit: "${GIT_COMMIT:unknown}"
        deregister: true
        
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      probes:
        enabled: true
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true
  tracing:
    sampling:
      probability: 1.0

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    private final UserService userService;
    private final DiscoveryClient discoveryClient;
    private final MeterRegistry meterRegistry;
    
    public UserController(UserService userService, 
                         DiscoveryClient discoveryClient,
                         MeterRegistry meterRegistry) {
        this.userService = userService;
        this.discoveryClient = discoveryClient;
        this.meterRegistry = meterRegistry;
    }
    
    @GetMapping
    @Timed(value = "user.get.all", description = "Time taken to get all users")
    public ResponseEntity<List<User>> getUsers() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<User> users = userService.getAllUsers();
            meterRegistry.counter("user.get.success").increment();
            return ResponseEntity.ok(users);
        } finally {
            sample.stop(Timer.builder("user.get.duration").register(meterRegistry));
        }
    }
    
    @GetMapping("/discovery/health")
    public ResponseEntity<Map<String, Object>> getDiscoveryHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("services", discoveryClient.getServices());
        health.put("description", "Service discovery health check");
        health.put("status", "UP");
        health.put("timestamp", Instant.now());
        return ResponseEntity.ok(health);
    }
}
```

### Enhanced Kubernetes Integration

```java
// Dependencies for Kubernetes with latest features
/*
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-fabric8-discovery</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-fabric8-config</artifactId>
</dependency>
*/

# Enhanced Kubernetes configuration
spring:
  application:
    name: user-service
  cloud:
    kubernetes:
      discovery:
        enabled: true
        all-namespaces: false
        wait-cache-ready: true
        cache-loading-timeout-seconds: 60
        include-external-name-services: false
        service-labels:
          environment: "${ENVIRONMENT:dev}"
          version: "${VERSION:1.0.0}"
        metadata:
          add-labels: true
          add-annotations: true
          labels-prefix: "k8s."
          annotations-prefix: "k8s."
      client:
        namespace: ${KUBERNETES_NAMESPACE:default}
        trust-certs: true
        api-version: v1
        connection-timeout: 10s
        request-timeout: 30s
      config:
        enabled: true
        sources:
          - name: user-service-config
            namespace: ${KUBERNETES_NAMESPACE:default}
          - name: shared-config
            namespace: config

@Component
public class EnhancedKubernetesServiceDiscovery {
    
    private final DiscoveryClient discoveryClient;
    private final KubernetesDiscoveryProperties properties;
    private final MeterRegistry meterRegistry;
    
    public EnhancedKubernetesServiceDiscovery(DiscoveryClient discoveryClient,
                                             KubernetesDiscoveryProperties properties,
                                             MeterRegistry meterRegistry) {
        this.discoveryClient = discoveryClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }
    
    @Observed(name = "service.discovery", contextualName = "get-service-instances")
    public List<ServiceInstance> getServiceInstances(String serviceName) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            meterRegistry.counter("service.discovery.success", "service", serviceName).increment();
            return instances;
        } catch (Exception e) {
            meterRegistry.counter("service.discovery.error", "service", serviceName).increment();
            throw e;
        } finally {
            sample.stop(Timer.builder("service.discovery.duration")
                    .tag("service", serviceName)
                    .register(meterRegistry));
        }
    }
    
    @EventListener
    public void handleInstanceRegistration(InstanceRegisteredEvent<?> event) {
        log.info("Service instance registered: {}", event.getConfig());
        meterRegistry.counter("service.registration").increment();
    }
    
    @EventListener
    public void handleInstanceDeregistration(InstanceDeregisteredEvent<?> event) {
        log.info("Service instance deregistered: {}", event.getConfig());
        meterRegistry.counter("service.deregistration").increment();
    }
}
```

## Advanced Configuration Management

### Enhanced Spring Cloud Config

```java
// Config Server with advanced features
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
    
    @Bean
    public EnvironmentPostProcessor customEnvironmentPostProcessor() {
        return new CustomEnvironmentPostProcessor();
    }
}

# Enhanced Config Server configuration
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/config-repo
          clone-on-start: true
          delete-untracked-branches: true
          force-pull: true
          default-label: main
          search-paths:
            - '{application}'
            - '{application}/{profile}'
            - 'shared'
            - 'shared/{profile}'
          repos:
            development:
              pattern: "*-dev,*-development"
              uri: https://github.com/your-org/config-repo-dev
            production:
              pattern: "*-prod,*-production"
              uri: https://github.com/your-org/config-repo-prod
              private-key: ${CONFIG_SERVER_PRIVATE_KEY:}
        encrypt:
          enabled: true
          key-store:
            location: classpath:config-server.jks
            password: ${KEYSTORE_PASSWORD:changeit}
            alias: config-server-key
        health:
          repositories:
            config-repo:
              label: main
              name: user-service
              profiles: dev,prod
        accept-empty: false
        overrides:
          management.endpoints.web.exposure.include: "*"

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    configprops:
      show-values: when-authorized
  tracing:
    sampling:
      probability: 1.0

// Enhanced config client
@Component
@ConfigurationProperties(prefix = "user-service")
@RefreshScope
@Observed
public class EnhancedUserServiceProperties {
    
    private String welcomeMessage = "Welcome to User Service 2.0";
    private int maxRetries = 3;
    private Duration timeout = Duration.ofSeconds(30);
    private List<String> allowedDomains = new ArrayList<>();
    private DatabaseConfig database = new DatabaseConfig();
    private CacheConfig cache = new CacheConfig();
    private SecurityConfig security = new SecurityConfig();
    
    @NestedConfigurationProperty
    public static class DatabaseConfig {
        private int maxConnections = 10;
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private boolean enableMetrics = true;
        private String dialect = "postgresql";
        
        // Getters and setters
    }
    
    @NestedConfigurationProperty
    public static class CacheConfig {
        private Duration ttl = Duration.ofMinutes(30);
        private int maxSize = 1000;
        private boolean enabled = true;
        
        // Getters and setters
    }
    
    @NestedConfigurationProperty
    public static class SecurityConfig {
        private Duration jwtExpiration = Duration.ofHours(1);
        private String jwtSecret = "default-secret";
        private boolean enableCsrf = true;
        
        // Getters and setters
    }
    
    // Getters and setters
}

@Component
public class ConfigurationMonitor {
    
    private final EnhancedUserServiceProperties properties;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;
    
    public ConfigurationMonitor(EnhancedUserServiceProperties properties,
                               MeterRegistry meterRegistry,
                               ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;
    }
    
    @EventListener
    @Observed(name = "config.refresh", contextualName = "handle-refresh-event")
    public void handleRefreshEvent(RefreshRemoteApplicationEvent event) {
        log.info("Configuration refreshed for destinations: {}", event.getDestinationService());
        meterRegistry.counter("config.refresh", "service", event.getOriginService()).increment();
        eventPublisher.publishEvent(new ConfigurationUpdatedEvent(properties));
    }
    
    @Scheduled(fixedDelay = 30000)
    public void recordConfigurationMetrics() {
        meterRegistry.gauge("config.timeout.seconds", properties.getTimeout().getSeconds());
        meterRegistry.gauge("config.max.retries", properties.getMaxRetries());
        meterRegistry.gauge("config.database.max.connections", properties.getDatabase().getMaxConnections());
    }
}
```

## Advanced Circuit Breaker with Resilience4j

```java
// Enhanced Resilience4j configuration
@Configuration
public class EnhancedResilience4jConfig {
    
    @Bean
    public CircuitBreakerConfigCustomizer circuitBreakerConfigCustomizer() {
        return CircuitBreakerConfigCustomizer.of("user-service", builder ->
                builder
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(2))
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .recordExceptions(Exception.class)
                        .ignoreExceptions(IllegalArgumentException.class, ValidationException.class)
                        .recordResult(result -> result instanceof ResponseEntity<?> response && 
                                response.getStatusCode().is5xxServerError())
        );
    }
    
    @Bean
    public RetryConfigCustomizer retryConfigCustomizer() {
        return RetryConfigCustomizer.of("user-service", builder ->
                builder
                        .maxAttempts(3)
                        .waitDuration(Duration.ofSeconds(1))
                        .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2))
                        .retryOnResult(result -> result instanceof ResponseEntity<?> response && 
                                response.getStatusCode().is5xxServerError())
                        .retryExceptions(IOException.class, TimeoutException.class, ConnectException.class)
                        .ignoreExceptions(IllegalArgumentException.class, ValidationException.class)
        );
    }
    
    @Bean
    public TimeLimiterConfigCustomizer timeLimiterConfigCustomizer() {
        return TimeLimiterConfigCustomizer.of("user-service", builder ->
                builder
                        .timeoutDuration(Duration.ofSeconds(5))
                        .cancelRunningFuture(true)
        );
    }
    
    @Bean
    public BulkheadConfigCustomizer bulkheadConfigCustomizer() {
        return BulkheadConfigCustomizer.of("user-service", builder ->
                builder
                        .maxConcurrentCalls(25)
                        .maxWaitDuration(Duration.ofSeconds(1))
        );
    }
}

@Service
@Observed
public class EnhancedExternalUserService {
    
    private final WebClient webClient;
    private final MeterRegistry meterRegistry;
    
    public EnhancedExternalUserService(WebClient.Builder webClientBuilder, MeterRegistry meterRegistry) {
        this.webClient = webClientBuilder
                .baseUrl("http://user-service")
                .filter(metricsFilter())
                .build();
        this.meterRegistry = meterRegistry;
    }
    
    @CircuitBreaker(name = "user-service", fallbackMethod = "fallbackGetUser")
    @Retry(name = "user-service")
    @TimeLimiter(name = "user-service")
    @Bulkhead(name = "user-service")
    @Observed(name = "external.user.get", contextualName = "get-user-async")
    public CompletableFuture<User> getUserAsync(Long userId) {
        return webClient
                .get()
                .uri("/api/v1/users/{id}", userId)
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, response -> 
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new UserNotFoundException("User not found: " + userId))))
                .onStatus(HttpStatus::is5xxServerError, response ->
                        Mono.error(new UserServiceException("User service error")))
                .bodyToMono(User.class)
                .timeout(Duration.ofSeconds(5))
                .toFuture();
    }
    
    @CircuitBreaker(name = "user-service", fallbackMethod = "fallbackSearchUsers")
    @Retry(name = "user-service")
    @TimeLimiter(name = "user-service")
    public Mono<List<User>> searchUsers(String query) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/search")
                        .queryParam("q", query)
                        .build())
                .retrieve()
                .bodyToFlux(User.class)
                .collectList()
                .doOnSuccess(users -> meterRegistry.counter("user.search.success").increment())
                .doOnError(error -> meterRegistry.counter("user.search.error").increment());
    }
    
    public CompletableFuture<User> fallbackGetUser(Long userId, Exception ex) {
        log.warn("Fallback triggered for user {}: {}", userId, ex.getMessage());
        meterRegistry.counter("user.fallback.triggered", "method", "getUser").increment();
        
        return CompletableFuture.completedFuture(User.builder()
                .id(userId)
                .firstName("Unknown")
                .lastName("User")
                .email("unknown@example.com")
                .build());
    }
    
    public Mono<List<User>> fallbackSearchUsers(String query, Exception ex) {
        log.warn("Fallback triggered for search '{}': {}", query, ex.getMessage());
        meterRegistry.counter("user.fallback.triggered", "method", "searchUsers").increment();
        return Mono.just(Collections.emptyList());
    }
    
    private ExchangeFilterFunction metricsFilter() {
        return (request, next) -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            return next.exchange(request)
                    .doOnTerminate(() -> sample.stop(Timer.builder("webclient.request")
                            .tag("method", request.method().name())
                            .tag("uri", request.url().getPath())
                            .register(meterRegistry)));
        };
    }
}

# Enhanced Resilience4j configuration
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        register-health-indicator: true
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 2s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - java.net.ConnectException
        ignore-exceptions:
          - java.lang.IllegalArgumentException
          - jakarta.validation.ValidationException
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        exponential-max-wait-duration: 10s
        retry-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
  timelimiter:
    instances:
      user-service:
        timeout-duration: 5s
        cancel-running-future: true
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 25
        max-wait-duration: 1s
  thread-pool-bulkhead:
    instances:
      user-service:
        core-thread-pool-size: 10
        max-thread-pool-size: 20
        queue-capacity: 100
        keep-alive-duration: 20s
```

## Next-Generation API Gateway

```java
// Enhanced Gateway with Spring Cloud Gateway 4.x features
@SpringBootApplication
public class NextGenApiGatewayApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(NextGenApiGatewayApplication.class, args);
    }
}

@Configuration
public class EnhancedGatewayConfig {
    
    @Bean
    public RouteLocator enhancedRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service-v2", r -> r
                        .path("/api/v2/users/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(config -> config
                                        .setName("user-service-cb-v2")
                                        .setFallbackUri("forward:/fallback/users")
                                        .setRouteId("user-service-v2")
                                )
                                .retry(config -> config
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET, HttpMethod.POST)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY)
                                        .setBackoff(Duration.ofSeconds(1), Duration.ofSeconds(10), 2, true)
                                )
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(enhancedUserKeyResolver())
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS)
                                )
                                .addRequestHeader("X-Gateway-Version", "2.0")
                                .addRequestHeader("X-Request-ID", "#{T(java.util.UUID).randomUUID().toString()}")
                                .modifyRequestBody(String.class, String.class, (exchange, body) -> {
                                    // Enhanced request modification
                                    return Mono.just(body);
                                })
                                .cache(Duration.ofMinutes(5))
                        )
                        .uri("lb://user-service")
                        .metadata("timeout", 5000)
                        .metadata("connect-timeout", 1000)
                )
                .route("websocket-enhanced", r -> r
                        .path("/ws/v2/**")
                        .filters(f -> f
                                .addRequestHeader("X-WebSocket-Version", "2.0")
                        )
                        .uri("lb:ws://websocket-service")
                )
                .build();
    }
    
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }
    
    @Bean
    public KeyResolver enhancedUserKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            String clientIp = getClientIp(exchange.getRequest());
            
            if (userId != null) {
                return Mono.just("user:" + userId);
            } else if (apiKey != null) {
                return Mono.just("api:" + apiKey.substring(0, Math.min(8, apiKey.length())));
            } else {
                return Mono.just("ip:" + clientIp);
            }
        };
    }
    
    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddress() != null 
                ? request.getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";
    }
    
    @Bean
    public GlobalFilter enhancedMetricsFilter(MeterRegistry meterRegistry) {
        return (exchange, chain) -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            ServerHttpRequest request = exchange.getRequest();
            
            return chain.filter(exchange)
                    .doFinally(signalType -> {
                        ServerHttpResponse response = exchange.getResponse();
                        sample.stop(Timer.builder("gateway.request.duration")
                                .tag("method", request.getMethod().name())
                                .tag("path", request.getPath().value())
                                .tag("status", String.valueOf(response.getStatusCode().value()))
                                .register(meterRegistry));
                        
                        meterRegistry.counter("gateway.request.total",
                                "method", request.getMethod().name(),
                                "status", String.valueOf(response.getStatusCode().value()))
                                .increment();
                    });
        };
    }
    
    @Bean
    public GlobalFilter correlationIdFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String correlationId = request.getHeaders().getFirst("X-Correlation-ID");
            
            if (correlationId == null) {
                correlationId = UUID.randomUUID().toString();
            }
            
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-Correlation-ID", correlationId)
                    .build();
            
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().add("X-Correlation-ID", correlationId);
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }
}

// Enhanced Gateway filters
@Component
public class EnhancedAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<EnhancedAuthGatewayFilterFactory.Config> {
    
    private final ReactiveJwtDecoder jwtDecoder;
    private final MeterRegistry meterRegistry;
    
    public EnhancedAuthGatewayFilterFactory(ReactiveJwtDecoder jwtDecoder, MeterRegistry meterRegistry) {
        super(Config.class);
        this.jwtDecoder = jwtDecoder;
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            if (config.skipAuth || isPublicPath(request.getPath().value())) {
                return chain.filter(exchange);
            }
            
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                meterRegistry.counter("gateway.auth.missing").increment();
                return handleUnauthorized(exchange);
            }
            
            String token = authHeader.substring(7);
            return jwtDecoder.decode(token)
                    .flatMap(jwt -> {
                        meterRegistry.counter("gateway.auth.success").increment();
                        
                        ServerHttpRequest mutatedRequest = request.mutate()
                                .header("X-User-ID", jwt.getSubject())
                                .header("X-User-Roles", String.join(",", jwt.getClaimAsStringList("roles")))
                                .build();
                        
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    })
                    .onErrorResume(error -> {
                        meterRegistry.counter("gateway.auth.failed").increment();
                        log.warn("JWT validation failed: {}", error.getMessage());
                        return handleUnauthorized(exchange);
                    });
        };
    }
    
    private boolean isPublicPath(String path) {
        return path.startsWith("/api/v1/public/") || 
               path.startsWith("/health") || 
               path.startsWith("/actuator/");
    }
    
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        
        String body = """
                {
                    "error": "UNAUTHORIZED",
                    "message": "Valid authentication required",
                    "timestamp": "%s"
                }
                """.formatted(Instant.now());
        
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }
    
    public static class Config {
        private boolean skipAuth = false;
        
        public boolean isSkipAuth() { return skipAuth; }
        public void setSkipAuth(boolean skipAuth) { this.skipAuth = skipAuth; }
    }
}

# Enhanced Gateway configuration
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 1000
        response-timeout: 5s
        pool:
          type: elastic
          max-idle-time: 15s
          max-life-time: 60s
        wiretap: true
      httpserver:
        wiretap: true
      global-filter:
        websocket-routing:
          enabled: true
      default-filters:
        - name: AddResponseHeader
          args:
            name: X-Response-Default-Gateway
            value: Next-Gen-Gateway
        - name: EnhancedAuth
          args:
            skip-auth: false
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
          predicates:
            - name: Path
              args:
                pattern: "'/api/'+serviceId+'/**'"
          filters:
            - name: StripPrefix
              args:
                parts: 1

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    gateway:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

## Advanced Observability

```java
// Enhanced observability configuration
@Configuration
@EnableObservability
public class ObservabilityConfig {
    
    @Bean
    public ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        
        // Add handlers
        registry.observationConfig()
                .observationHandler(new PropagatingSenderTracingObservationHandler<>(
                        Tracing.current().tracer()))
                .observationHandler(new PropagatingSenderMeterObservationHandler<>(
                        Metrics.globalRegistry))
                .observationHandler(new ObservationTextPublisher())
                .observationPredicate((name, context) -> !name.startsWith("spring.security"));
        
        return registry;
    }
    
    @Bean
    public NewRelic newRelicMeterRegistry() {
        return NewRelicMeterRegistry.builder(NewRelicConfig.DEFAULT)
                .commonTags("service", "user-service", "version", "2.0.0")
                .build();
    }
    
    @Bean
    public CompositeMeterRegistry compositeMeterRegistry(
            NewRelicMeterRegistry newRelicRegistry,
            PrometheusMeterRegistry prometheusRegistry) {
        
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(newRelicRegistry);
        composite.add(prometheusRegistry);
        return composite;
    }
}

@Service
@Observed
public class ObservableUserService {
    
    private final UserRepository userRepository;
    private final ObservationRegistry observationRegistry;
    
    public ObservableUserService(UserRepository userRepository, 
                                ObservationRegistry observationRegistry) {
        this.userRepository = userRepository;
        this.observationRegistry = observationRegistry;
    }
    
    @Observed(name = "user.create", 
              contextualName = "user-creation",
              lowCardinalityKeyValues = {"operation", "create"})
    public User createUser(CreateUserRequest request) {
        return Observation.createNotStarted("user.validation", observationRegistry)
                .observe(() -> {
                    validateUserRequest(request);
                    return userRepository.save(mapToUser(request));
                });
    }
    
    @Observed(name = "user.search",
              contextualName = "user-search", 
              lowCardinalityKeyValues = {"operation", "search"})
    public Page<User> searchUsers(String query, Pageable pageable) {
        return Observation.createNotStarted("user.database.search", observationRegistry)
                .lowCardinalityKeyValue("query.length", String.valueOf(query.length()))
                .highCardinalityKeyValue("query.hash", String.valueOf(query.hashCode()))
                .observe(() -> userRepository.findBySearchQuery(query, pageable));
    }
    
    private void validateUserRequest(CreateUserRequest request) {
        // Validation logic with span events
        Observation.start("user.validation.email", observationRegistry)
                .event(Observation.Event.of("validation.start"))
                .observe(() -> validateEmail(request.getEmail()));
    }
}

// Custom metrics
@Component
public class UserServiceMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter userCreatedCounter;
    private final Timer userCreationTimer;
    private final Gauge activeUsersGauge;
    
    public UserServiceMetrics(MeterRegistry meterRegistry, UserRepository userRepository) {
        this.meterRegistry = meterRegistry;
        this.userCreatedCounter = Counter.builder("users.created")
                .description("Number of users created")
                .tag("service", "user-service")
                .register(meterRegistry);
        
        this.userCreationTimer = Timer.builder("users.creation.duration")
                .description("Time taken to create a user")
                .register(meterRegistry);
        
        this.activeUsersGauge = Gauge.builder("users.active.count")
                .description("Number of active users")
                .register(meterRegistry, userRepository, repo -> repo.countByActiveTrue());
    }
    
    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        userCreatedCounter.increment(
                Tags.of(
                        Tag.of("domain", extractDomain(event.getUser().getEmail())),
                        Tag.of("department", event.getUser().getDepartment())
                )
        );
    }
    
    public Timer.Sample startCreationTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordCreationTime(Timer.Sample sample, String result) {
        sample.stop(Timer.builder("users.creation.duration")
                .tag("result", result)
                .register(meterRegistry));
    }
}

# Enhanced observability configuration
management:
  observations:
    key-values:
      service: user-service
      version: 2.0.0
  tracing:
    sampling:
      probability: 1.0
    baggage:
      enabled: true
      remote-fields:
        - user-id
        - correlation-id
      correlation:
        enabled: true
        fields:
          - user-id
          - correlation-id
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
        user.creation.duration: true
      percentiles:
        http.server.requests: 0.5,0.95,0.99
        user.creation.duration: 0.5,0.95,0.99
    export:
      prometheus:
        enabled: true
        step: 10s
      newrelic:
        enabled: true
        api-key: ${NEW_RELIC_API_KEY:}
        account-id: ${NEW_RELIC_ACCOUNT_ID:}
        step: 30s

logging:
  pattern:
    level: '%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{user-id:-}]'
```

Spring Cloud 2023.x represents the pinnacle of cloud-native microservices development, offering enhanced observability, improved performance, advanced security features, and seamless integration with modern cloud platforms while maintaining the simplicity and power that Spring developers expect.
