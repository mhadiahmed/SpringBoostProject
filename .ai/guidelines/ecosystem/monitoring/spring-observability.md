# Spring Observability and Monitoring Guidelines

## Overview

Comprehensive guidelines for implementing observability in Spring Boot applications including metrics, tracing, logging, health checks, and integration with monitoring platforms like Prometheus, Grafana, Jaeger, and ELK stack.

## Metrics with Micrometer

### Basic Configuration

```yaml
# application.yml - Metrics Configuration
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    metrics:
      enabled: true
    prometheus:
      enabled: true
    health:
      show-details: always
      probes:
        enabled: true
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
        spring.data.repository.invocations: true
        custom.user.operations: true
      percentiles:
        http.server.requests: 0.5,0.75,0.95,0.99
        spring.data.repository.invocations: 0.5,0.95,0.99
    export:
      prometheus:
        enabled: true
        step: 10s
        descriptions: true
      cloudwatch:
        enabled: ${CLOUDWATCH_METRICS_ENABLED:false}
        namespace: SpringBoost
        step: 60s
      newrelic:
        enabled: ${NEWRELIC_METRICS_ENABLED:false}
        api-key: ${NEW_RELIC_API_KEY:}
        account-id: ${NEW_RELIC_ACCOUNT_ID:}
        step: 30s
    tags:
      application: spring-boost
      environment: ${ENVIRONMENT:dev}
      version: ${APPLICATION_VERSION:1.0.0}
      team: backend
```

### Custom Metrics Configuration

```java
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
            @Value("${spring.application.name}") String applicationName,
            @Value("${ENVIRONMENT:dev}") String environment) {
        
        return registry -> registry.config()
                .commonTags(
                        "application", applicationName,
                        "environment", environment,
                        "instance", getInstanceId(),
                        "version", getVersion()
                );
    }
    
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
    
    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }
    
    @Bean
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
    
    @Bean
    @ConditionalOnProperty(name = "management.metrics.export.cloudwatch.enabled", havingValue = "true")
    public CloudWatchMeterRegistry cloudWatchMeterRegistry() {
        return CloudWatchMeterRegistry.builder(CloudWatchConfig.DEFAULT)
                .cloudWatchAsyncClient(CloudWatchAsyncClient.builder()
                        .region(Region.US_EAST_1)
                        .build())
                .build();
    }
    
    private String getInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String getVersion() {
        return getClass().getPackage().getImplementationVersion() != null 
                ? getClass().getPackage().getImplementationVersion() 
                : "dev";
    }
}
```

### Business Metrics

```java
@Component
public class UserMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter userRegistrationCounter;
    private final Counter userLoginCounter;
    private final Timer userOperationTimer;
    private final Gauge activeUsersGauge;
    private final UserRepository userRepository;
    
    public UserMetrics(MeterRegistry meterRegistry, UserRepository userRepository) {
        this.meterRegistry = meterRegistry;
        this.userRepository = userRepository;
        
        this.userRegistrationCounter = Counter.builder("user.registrations")
                .description("Number of user registrations")
                .tag("type", "registration")
                .register(meterRegistry);
        
        this.userLoginCounter = Counter.builder("user.logins")
                .description("Number of user logins")
                .register(meterRegistry);
        
        this.userOperationTimer = Timer.builder("user.operation.duration")
                .description("User operation execution time")
                .register(meterRegistry);
        
        this.activeUsersGauge = Gauge.builder("user.active.count")
                .description("Number of active users")
                .register(meterRegistry, userRepository, repo -> repo.countByActiveTrue());
    }
    
    public void incrementRegistrations(String department) {
        userRegistrationCounter.increment(Tags.of("department", department));
    }
    
    public void incrementLogins(String userType) {
        userLoginCounter.increment(Tags.of("user_type", userType));
    }
    
    public Timer.Sample startUserOperation() {
        return Timer.start(meterRegistry);
    }
    
    public void recordUserOperation(Timer.Sample sample, String operation, String status) {
        sample.stop(Timer.builder("user.operation.duration")
                .tag("operation", operation)
                .tag("status", status)
                .register(meterRegistry));
    }
    
    @EventListener
    public void handleUserRegistration(UserRegisteredEvent event) {
        incrementRegistrations(event.getUser().getDepartment());
        
        // Record user demographics
        meterRegistry.counter("user.registrations.by.source", 
                "source", event.getRegistrationSource()).increment();
    }
    
    @EventListener
    public void handleUserLogin(UserLoginEvent event) {
        incrementLogins(event.getUserType());
        
        // Track login methods
        meterRegistry.counter("user.logins.by.method", 
                "method", event.getLoginMethod()).increment();
    }
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void recordPeriodicMetrics() {
        // Record current statistics
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByActiveTrue();
        
        meterRegistry.gauge("user.total.count", totalUsers);
        meterRegistry.gauge("user.active.percentage", 
                totalUsers > 0 ? (activeUsers * 100.0 / totalUsers) : 0);
    }
}

@Service
public class ApplicationMetrics {
    
    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;
    
    public ApplicationMetrics(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
        
        // Custom gauges
        Gauge.builder("database.connections.active")
                .description("Active database connections")
                .register(meterRegistry, this, ApplicationMetrics::getActiveConnections);
        
        Gauge.builder("jvm.memory.usage.ratio")
                .description("JVM memory usage ratio")
                .register(meterRegistry, this, ApplicationMetrics::getMemoryUsageRatio);
    }
    
    private double getActiveConnections() {
        try {
            if (dataSource instanceof HikariDataSource hikariDS) {
                return hikariDS.getHikariPoolMXBean().getActiveConnections();
            }
        } catch (Exception e) {
            log.warn("Failed to get active connections", e);
        }
        return 0;
    }
    
    private double getMemoryUsageRatio() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }
}
```

### Service-Level Metrics

```java
@Service
@Component
public class UserService {
    
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;
    
    public UserService(UserRepository userRepository, MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.meterRegistry = meterRegistry;
    }
    
    @Timed(value = "user.service.create", description = "Time taken to create user")
    @Counted(value = "user.service.create.attempts", description = "User creation attempts")
    public User createUser(CreateUserRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Validation metrics
            meterRegistry.counter("user.validation.attempts").increment();
            validateUser(request);
            
            // Creation metrics
            User user = User.builder()
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .build();
            
            User savedUser = userRepository.save(user);
            
            // Success metrics
            meterRegistry.counter("user.creation.success", 
                    "department", savedUser.getDepartment()).increment();
            
            return savedUser;
            
        } catch (ValidationException e) {
            meterRegistry.counter("user.creation.validation.error").increment();
            throw e;
        } catch (DataIntegrityViolationException e) {
            meterRegistry.counter("user.creation.duplicate.error").increment();
            throw new UserAlreadyExistsException("User already exists");
        } catch (Exception e) {
            meterRegistry.counter("user.creation.error").increment();
            throw e;
        } finally {
            sample.stop(Timer.builder("user.creation.duration")
                    .register(meterRegistry));
        }
    }
    
    @Timed("user.service.update")
    public User updateUser(Long userId, UpdateUserRequest request) {
        return Timer.Sample.start(meterRegistry)
                .stop(Timer.builder("user.update.duration")
                        .tag("operation", "update")
                        .register(meterRegistry))
                .recordCallable(() -> {
                    User user = findUserById(userId);
                    
                    // Record what fields are being updated
                    recordFieldUpdates(user, request);
                    
                    user.setFirstName(request.getFirstName());
                    user.setLastName(request.getLastName());
                    
                    return userRepository.save(user);
                });
    }
    
    private void recordFieldUpdates(User existing, UpdateUserRequest request) {
        if (!Objects.equals(existing.getFirstName(), request.getFirstName())) {
            meterRegistry.counter("user.field.updates", "field", "firstName").increment();
        }
        if (!Objects.equals(existing.getLastName(), request.getLastName())) {
            meterRegistry.counter("user.field.updates", "field", "lastName").increment();
        }
    }
}
```

## Distributed Tracing

### Configuration

```yaml
# application.yml - Tracing Configuration
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
    baggage:
      enabled: true
      remote-fields:
        - user-id
        - correlation-id
        - request-source
      correlation:
        enabled: true
        fields:
          - user-id
          - correlation-id
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}

logging:
  pattern:
    level: '%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-},%X{baggage.user-id:-}]'
```

### Tracing Configuration

```java
@Configuration
public class TracingConfig {
    
    @Bean
    public ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        
        // Add observation handlers
        registry.observationConfig()
                .observationHandler(new PropagatingSenderTracingObservationHandler<>(
                        Tracing.current().tracer()))
                .observationHandler(new PropagatingSenderMeterObservationHandler<>(
                        Metrics.globalRegistry))
                .observationHandler(new ObservationTextPublisher())
                .observationPredicate((name, context) -> 
                        shouldTrace(name, context));
        
        return registry;
    }
    
    @Bean
    public Sender sender() {
        return OkHttpSender.create("http://localhost:9411/api/v2/spans");
    }
    
    @Bean
    public AsyncReporter<Span> spanReporter() {
        return AsyncReporter.create(sender());
    }
    
    @Bean
    public BraveCurrentTraceContext braveCurrentTraceContext() {
        return BraveCurrentTraceContext.newBuilder()
                .addScopeDecorator(MDCScopeDecorator.get())
                .build();
    }
    
    @Bean
    public Tracing tracing() {
        return Tracing.newBuilder()
                .localServiceName("spring-boost")
                .spanReporter(spanReporter())
                .currentTraceContext(braveCurrentTraceContext())
                .build();
    }
    
    private boolean shouldTrace(String name, Observation.Context context) {
        // Exclude health checks and internal operations
        return !name.startsWith("spring.security") &&
               !name.contains("actuator") &&
               !name.contains("health");
    }
}
```

### Custom Tracing

```java
@Service
public class UserTraceService {
    
    private final UserRepository userRepository;
    private final Tracer tracer;
    private final ObservationRegistry observationRegistry;
    
    public UserTraceService(UserRepository userRepository, 
                           Tracer tracer,
                           ObservationRegistry observationRegistry) {
        this.userRepository = userRepository;
        this.tracer = tracer;
        this.observationRegistry = observationRegistry;
    }
    
    @NewSpan("user-service.find-by-id")
    public User findById(@SpanTag("user.id") Long userId) {
        Span span = tracer.nextSpan()
                .name("database.user.findById")
                .tag("db.operation", "findById")
                .tag("user.id", String.valueOf(userId))
                .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
            
            span.tag("user.found", "true");
            span.tag("user.department", user.getDepartment());
            
            return user;
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            span.tag("user.found", "false");
            throw e;
        } finally {
            span.end();
        }
    }
    
    @Observed(name = "user.service.create", 
              contextualName = "user-creation",
              lowCardinalityKeyValues = {"operation", "create"})
    public User createUser(CreateUserRequest request) {
        return Observation.createNotStarted("user.creation.process", observationRegistry)
                .lowCardinalityKeyValue("department", request.getDepartment())
                .highCardinalityKeyValue("email.domain", extractDomain(request.getEmail()))
                .observe(() -> {
                    // Validation span
                    Observation.start("user.validation", observationRegistry)
                            .observe(() -> validateUserRequest(request));
                    
                    // Database span
                    return Observation.start("user.database.save", observationRegistry)
                            .observe(() -> {
                                User user = mapToUser(request);
                                return userRepository.save(user);
                            });
                });
    }
    
    @NewSpan("user-service.search")
    public List<User> searchUsers(@SpanTag("search.query") String query,
                                 @SpanTag("search.page") int page) {
        
        Span span = tracer.nextSpan()
                .name("user.search.complex")
                .tag("search.query.length", String.valueOf(query.length()))
                .tag("search.page", String.valueOf(page))
                .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // Add baggage for downstream services
            BaggageField.create("search.query").updateValue(query);
            BaggageField.create("user.context").updateValue("search");
            
            List<User> results = performSearch(query, page);
            
            span.tag("search.results.count", String.valueOf(results.size()));
            span.event("search.completed");
            
            return results;
        } finally {
            span.end();
        }
    }
    
    private List<User> performSearch(String query, int page) {
        // Create child span for actual search
        Span searchSpan = tracer.nextSpan()
                .name("database.search")
                .tag("db.operation", "search")
                .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(searchSpan)) {
            // Simulate search logic
            return userRepository.findByEmailContainingIgnoreCase(query);
        } finally {
            searchSpan.end();
        }
    }
    
    private String extractDomain(String email) {
        return email.substring(email.indexOf('@') + 1);
    }
    
    private void validateUserRequest(CreateUserRequest request) {
        // Validation logic with events
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.event("validation.email.start");
            // Validate email
            currentSpan.event("validation.email.complete");
            
            currentSpan.event("validation.domain.start");
            // Validate domain
            currentSpan.event("validation.domain.complete");
        }
    }
}
```

## Health Checks

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
                        .withDetail("maximumPoolSize", getMaxPoolSize())
                        .withDetail("activeConnections", getActiveConnections())
                        .build();
            }
        } catch (SQLException ex) {
            return Health.down()
                    .withDetail("database", "Unavailable")
                    .withDetail("error", ex.getMessage())
                    .withException(ex)
                    .build();
        }
        
        return Health.down()
                .withDetail("database", "Connection validation failed")
                .build();
    }
    
    private int getMaxPoolSize() {
        if (dataSource instanceof HikariDataSource hikariDS) {
            return hikariDS.getMaximumPoolSize();
        }
        return -1;
    }
    
    private int getActiveConnections() {
        if (dataSource instanceof HikariDataSource hikariDS) {
            return hikariDS.getHikariPoolMXBean().getActiveConnections();
        }
        return -1;
    }
}

@Component
public class ExternalServiceHealthIndicator implements HealthIndicator {
    
    private final RestTemplate restTemplate;
    private final String externalServiceUrl;
    
    public ExternalServiceHealthIndicator(RestTemplate restTemplate,
                                         @Value("${external.service.url}") String externalServiceUrl) {
        this.restTemplate = restTemplate;
        this.externalServiceUrl = externalServiceUrl;
    }
    
    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.getForEntity(
                    externalServiceUrl + "/health", String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                        .withDetail("externalService", "Available")
                        .withDetail("responseTime", responseTime + "ms")
                        .withDetail("status", response.getStatusCode())
                        .build();
            } else {
                return Health.down()
                        .withDetail("externalService", "Unhealthy")
                        .withDetail("status", response.getStatusCode())
                        .withDetail("responseTime", responseTime + "ms")
                        .build();
            }
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("externalService", "Unavailable")
                    .withDetail("error", ex.getMessage())
                    .withException(ex)
                    .build();
        }
    }
}

@Component
public class BusinessHealthIndicator implements HealthIndicator {
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public BusinessHealthIndicator(UserRepository userRepository,
                                  RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        // Check critical business metrics
        try {
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.countByActiveTrue();
            
            builder.withDetail("totalUsers", totalUsers)
                   .withDetail("activeUsers", activeUsers)
                   .withDetail("activeUserPercentage", 
                           totalUsers > 0 ? (activeUsers * 100.0 / totalUsers) : 0);
            
            // Check Redis connectivity
            String ping = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            
            builder.withDetail("redis", "PONG".equals(ping) ? "Available" : "Unavailable");
            
            // Business rule checks
            if (activeUsers < (totalUsers * 0.1)) {
                builder.down().withDetail("warning", "Low active user percentage");
            }
            
        } catch (Exception ex) {
            builder.down()
                   .withDetail("error", ex.getMessage())
                   .withException(ex);
        }
        
        return builder.build();
    }
}
```

### Readiness and Liveness Probes

```java
@Component
public class ApplicationReadinessIndicator implements HealthIndicator {
    
    private final ApplicationContext applicationContext;
    private final List<ExternalService> externalServices;
    private volatile boolean ready = false;
    
    public ApplicationReadinessIndicator(ApplicationContext applicationContext,
                                        List<ExternalService> externalServices) {
        this.applicationContext = applicationContext;
        this.externalServices = externalServices;
    }
    
    @Override
    public Health health() {
        if (!ready) {
            return Health.down()
                    .withDetail("readiness", "Application is not ready")
                    .withDetail("reason", "Still initializing")
                    .build();
        }
        
        // Check external dependencies
        for (ExternalService service : externalServices) {
            if (!service.isAvailable()) {
                return Health.down()
                        .withDetail("readiness", "External dependency unavailable")
                        .withDetail("service", service.getName())
                        .build();
            }
        }
        
        return Health.up()
                .withDetail("readiness", "Application is ready")
                .withDetail("startupTime", getStartupTime())
                .build();
    }
    
    @EventListener
    public void handleApplicationReady(ApplicationReadyEvent event) {
        this.ready = true;
        log.info("Application is ready for traffic");
    }
    
    @EventListener
    public void handleApplicationFailure(ApplicationFailedEvent event) {
        this.ready = false;
        log.error("Application failed to start", event.getException());
    }
    
    private String getStartupTime() {
        if (applicationContext instanceof ConfigurableApplicationContext ctx) {
            return String.valueOf(ctx.getStartupDate());
        }
        return "unknown";
    }
}

@Component
public class ApplicationLivenessIndicator implements HealthIndicator {
    
    private final MeterRegistry meterRegistry;
    private volatile boolean alive = true;
    
    public ApplicationLivenessIndicator(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public Health health() {
        if (!alive) {
            return Health.down()
                    .withDetail("liveness", "Application is not responding")
                    .build();
        }
        
        // Check for critical resources
        try {
            // Check memory usage
            double memoryUsage = getMemoryUsage();
            if (memoryUsage > 0.95) {
                return Health.down()
                        .withDetail("liveness", "High memory usage")
                        .withDetail("memoryUsage", memoryUsage)
                        .build();
            }
            
            // Check thread deadlocks
            if (hasDeadlocks()) {
                return Health.down()
                        .withDetail("liveness", "Thread deadlock detected")
                        .build();
            }
            
            return Health.up()
                    .withDetail("liveness", "Application is alive")
                    .withDetail("memoryUsage", memoryUsage)
                    .withDetail("threadCount", getThreadCount())
                    .build();
                    
        } catch (Exception e) {
            return Health.down()
                    .withDetail("liveness", "Health check failed")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
    
    private double getMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }
    
    private boolean hasDeadlocks() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        return deadlockedThreads != null && deadlockedThreads.length > 0;
    }
    
    private int getThreadCount() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }
}
```

## Monitoring Integration

### Prometheus Integration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "spring_boost_rules.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093

scrape_configs:
  - job_name: 'spring-boost'
    static_configs:
      - targets: ['spring-boost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    scrape_timeout: 5s
    
  - job_name: 'spring-boost-kubernetes'
    kubernetes_sd_configs:
      - role: endpoints
        namespaces:
          names:
            - spring-boost
    relabel_configs:
      - source_labels: [__meta_kubernetes_service_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_service_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
```

### Grafana Dashboard Configuration

```json
{
  "dashboard": {
    "id": null,
    "title": "Spring Boost Application Dashboard",
    "tags": ["spring-boot", "spring-boost", "microservices"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Application Health",
        "type": "stat",
        "targets": [
          {
            "expr": "up{job=\"spring-boost\"}",
            "legendFormat": "{{instance}}"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "color": {
              "mode": "thresholds"
            },
            "thresholds": {
              "steps": [
                {"color": "red", "value": 0},
                {"color": "green", "value": 1}
              ]
            }
          }
        }
      },
      {
        "id": 2,
        "title": "HTTP Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count{job=\"spring-boost\"}[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ]
      },
      {
        "id": 3,
        "title": "Response Time (95th percentile)",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job=\"spring-boost\"}[5m]))",
            "legendFormat": "95th percentile"
          }
        ]
      },
      {
        "id": 4,
        "title": "JVM Memory Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{job=\"spring-boost\"} / jvm_memory_max_bytes{job=\"spring-boost\"}",
            "legendFormat": "{{area}}"
          }
        ]
      },
      {
        "id": 5,
        "title": "Database Connections",
        "type": "graph",
        "targets": [
          {
            "expr": "hikaricp_connections_active{job=\"spring-boost\"}",
            "legendFormat": "Active"
          },
          {
            "expr": "hikaricp_connections_idle{job=\"spring-boost\"}",
            "legendFormat": "Idle"
          }
        ]
      },
      {
        "id": 6,
        "title": "User Registrations",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(user_registrations_total{job=\"spring-boost\"}[5m])",
            "legendFormat": "{{department}}"
          }
        ]
      }
    ],
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "refresh": "30s"
  }
}
```

This comprehensive observability guide provides production-ready patterns for monitoring Spring Boot applications with comprehensive metrics, tracing, health checks, and integration with popular monitoring platforms.
