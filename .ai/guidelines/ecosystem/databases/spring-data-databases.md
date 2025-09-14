# Spring Data Database Integration Guidelines

## Overview

Comprehensive guidelines for integrating Spring Boot applications with various databases including PostgreSQL, MySQL, MongoDB, Redis, and others. Covers configuration, optimization, transactions, and best practices.

## PostgreSQL Integration

### Configuration and Setup

```yaml
# application.yml - PostgreSQL Configuration
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/springboost
    username: ${DB_USERNAME:springboost}
    password: ${DB_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: SpringBoostHikariCP
      maximum-pool-size: ${DB_POOL_MAX_SIZE:20}
      minimum-idle: ${DB_POOL_MIN_IDLE:5}
      connection-timeout: ${DB_CONNECTION_TIMEOUT:30000}
      idle-timeout: ${DB_IDLE_TIMEOUT:600000}
      max-lifetime: ${DB_MAX_LIFETIME:1800000}
      leak-detection-threshold: ${DB_LEAK_DETECTION:60000}
      connection-test-query: SELECT 1
      
  jpa:
    database: postgresql
    database-platform: org.hibernate.dialect.PostgreSQL15Dialect
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:validate}
      naming:
        physical-strategy: org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
        implicit-strategy: org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy
    properties:
      hibernate:
        format_sql: ${JPA_FORMAT_SQL:false}
        show_sql: ${JPA_SHOW_SQL:false}
        use_sql_comments: false
        jdbc:
          batch_size: 25
          order_inserts: true
          order_updates: true
          batch_versioned_data: true
        connection:
          provider_disables_autocommit: true
        query:
          in_clause_parameter_padding: true
          fail_on_pagination_over_collection_fetch: true
        temp:
          use_jdbc_metadata_defaults: false
    open-in-view: false

  # Liquibase for database migrations
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
    enabled: ${LIQUIBASE_ENABLED:true}
    drop-first: false
    contexts: ${LIQUIBASE_CONTEXTS:default}
```

### Entity Configuration

```java
@Entity
@Table(name = "users", 
       indexes = {
           @Index(name = "idx_user_email", columnList = "email", unique = true),
           @Index(name = "idx_user_department", columnList = "department"),
           @Index(name = "idx_user_created_at", columnList = "created_at")
       })
@EntityListeners(AuditingEntityListener.class)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "email", nullable = false, unique = true, length = 255)
    @Email
    @NotBlank
    private String email;
    
    @Column(name = "first_name", nullable = false, length = 100)
    @NotBlank
    @Size(min = 2, max = 100)
    private String firstName;
    
    @Column(name = "last_name", nullable = false, length = 100)
    @NotBlank
    @Size(min = 2, max = 100)
    private String lastName;
    
    @Column(name = "department", length = 100)
    private String department;
    
    @Column(name = "active", nullable = false)
    private Boolean active = true;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    // One-to-Many relationship with proper fetch strategy
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<UserRole> userRoles = new ArrayList<>();
    
    // Many-to-One with lazy loading
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", foreignKey = @ForeignKey(name = "fk_user_department"))
    private Department departmentEntity;
    
    // JSON column for PostgreSQL
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();
    
    // Constructors, getters, setters
}

@Entity
@Table(name = "departments")
public class Department {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, unique = true)
    private String name;
    
    @Column(name = "location")
    private String location;
    
    @OneToMany(mappedBy = "departmentEntity", fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();
    
    // Constructors, getters, setters
}
```

### Repository Implementation

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    
    // Optimized queries with proper indexing
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);
    
    @Query("SELECT u FROM User u JOIN FETCH u.departmentEntity WHERE u.active = true")
    List<User> findActiveUsersWithDepartment();
    
    // Pagination with sorting
    @Query("SELECT u FROM User u WHERE u.department = :department")
    Page<User> findByDepartmentPaginated(@Param("department") String department, Pageable pageable);
    
    // Bulk operations for better performance
    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.lastLogin < :cutoffDate")
    int deactivateInactiveUsers(@Param("cutoffDate") Instant cutoffDate);
    
    // Native query for complex operations
    @Query(value = """
           SELECT u.*, d.name as department_name, 
                  COUNT(ur.id) as role_count
           FROM users u 
           LEFT JOIN departments d ON u.department_id = d.id
           LEFT JOIN user_roles ur ON u.id = ur.user_id
           WHERE u.active = true
           GROUP BY u.id, d.name
           HAVING COUNT(ur.id) > :minRoles
           ORDER BY u.created_at DESC
           """, nativeQuery = true)
    List<Object[]> findActiveUsersWithMultipleRoles(@Param("minRoles") int minRoles);
    
    // Custom methods for better performance
    @Query("SELECT COUNT(u) FROM User u WHERE u.department = :department AND u.active = true")
    long countActiveUsersByDepartment(@Param("department") String department);
    
    // Projection for read-only data
    @Query("SELECT new com.springboost.dto.UserSummaryDto(u.id, u.firstName, u.lastName, u.email, u.department) " +
           "FROM User u WHERE u.active = true ORDER BY u.lastName")
    List<UserSummaryDto> findActiveUserSummaries();
}

@Repository
@Transactional
public class UserRepositoryImpl {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<User> findUsersWithDynamicCriteria(UserSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        // Join fetch for performance
        root.fetch("departmentEntity", JoinType.LEFT);
        
        List<Predicate> predicates = buildPredicates(cb, root, criteria);
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.asc(root.get("lastName")), cb.asc(root.get("firstName")));
        
        return entityManager.createQuery(query)
                .setHint(QueryHints.HINT_FETCH_SIZE, 50)
                .getResultList();
    }
    
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<User> root, UserSearchCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getEmail() != null) {
            predicates.add(cb.like(cb.lower(root.get("email")), 
                    "%" + criteria.getEmail().toLowerCase() + "%"));
        }
        
        if (criteria.getDepartment() != null) {
            predicates.add(cb.equal(root.get("department"), criteria.getDepartment()));
        }
        
        if (criteria.getActive() != null) {
            predicates.add(cb.equal(root.get("active"), criteria.getActive()));
        }
        
        return predicates;
    }
}
```

## MySQL Integration

### Configuration and Optimization

```yaml
# application.yml - MySQL Configuration
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/springboost?useSSL=true&serverTimezone=UTC&useLegacyDatetimeCode=false&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:springboost}
    password: ${DB_PASSWORD:password}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      pool-name: SpringBoostMySQLCP
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
      
  jpa:
    database: mysql
    database-platform: org.hibernate.dialect.MySQL8Dialect
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
    properties:
      hibernate:
        jdbc:
          batch_size: 25
          order_inserts: true
          order_updates: true
        connection:
          provider_disables_autocommit: true
        dialect:
          storage_engine: innodb
```

### MySQL-specific Entity Features

```java
@Entity
@Table(name = "products", 
       indexes = {
           @Index(name = "idx_product_name_fulltext", columnList = "name"),
           @Index(name = "idx_product_category", columnList = "category_id"),
           @Index(name = "idx_product_price", columnList = "price")
       })
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;
    
    // MySQL-specific JSON column
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "JSON")
    private Map<String, Object> attributes;
    
    // Full-text search support
    @Formula("MATCH(name, description) AGAINST(?1 IN NATURAL LANGUAGE MODE)")
    private Double relevanceScore;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructors, getters, setters
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Full-text search
    @Query(value = """
           SELECT *, MATCH(name, description) AGAINST(?1 IN NATURAL LANGUAGE MODE) as score 
           FROM products 
           WHERE MATCH(name, description) AGAINST(?1 IN NATURAL LANGUAGE MODE)
           ORDER BY score DESC
           """, nativeQuery = true)
    List<Product> searchByFullText(String searchTerm);
    
    // JSON queries
    @Query(value = "SELECT * FROM products WHERE JSON_EXTRACT(attributes, '$.color') = ?1", nativeQuery = true)
    List<Product> findByColor(String color);
    
    @Query(value = """
           SELECT * FROM products 
           WHERE JSON_CONTAINS(attributes, JSON_OBJECT('tags', JSON_ARRAY(?1)))
           """, nativeQuery = true)
    List<Product> findByTag(String tag);
}
```

## MongoDB Integration

### Configuration

```yaml
# application.yml - MongoDB Configuration
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/springboost}
      username: ${MONGODB_USERNAME:}
      password: ${MONGODB_PASSWORD:}
      database: ${MONGODB_DATABASE:springboost}
      auto-index-creation: true
      
  # MongoDB-specific configuration
  mongodb:
    embedded:
      version: 4.4.0
```

### Document Entities

```java
@Document(collection = "users")
@CompoundIndex(def = "{'email': 1}", unique = true)
@CompoundIndex(def = "{'department': 1, 'active': 1}")
@CompoundIndex(def = "{'createdAt': -1}")
public class UserDocument {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    @Field("email")
    private String email;
    
    @Field("firstName")
    private String firstName;
    
    @Field("lastName")
    private String lastName;
    
    @Field("department")
    private String department;
    
    @Field("active")
    private Boolean active = true;
    
    @Field("metadata")
    private Map<String, Object> metadata = new HashMap<>();
    
    @Field("roles")
    private List<String> roles = new ArrayList<>();
    
    @Field("address")
    private Address address;
    
    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;
    
    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;
    
    @Version
    @Field("version")
    private Long version;
    
    // Embedded document
    public static class Address {
        private String street;
        private String city;
        private String country;
        private String postalCode;
        
        // Constructors, getters, setters
    }
    
    // Constructors, getters, setters
}

@Repository
public interface UserDocumentRepository extends MongoRepository<UserDocument, String> {
    
    Optional<UserDocument> findByEmail(String email);
    List<UserDocument> findByDepartmentAndActiveTrue(String department);
    List<UserDocument> findByRolesContaining(String role);
    
    // Aggregation queries
    @Aggregation(pipeline = {
        "{ $match: { active: true } }",
        "{ $group: { _id: '$department', count: { $sum: 1 } } }",
        "{ $sort: { count: -1 } }"
    })
    List<DepartmentCount> countUsersByDepartment();
    
    // Text search
    @Query("{ $text: { $search: ?0 } }")
    List<UserDocument> findByTextSearch(String searchTerm);
    
    // Complex queries
    @Query("{ 'metadata.?0': ?1 }")
    List<UserDocument> findByMetadataField(String field, Object value);
    
    @Query("{ 'address.city': ?0, 'active': true }")
    List<UserDocument> findActiveUsersByCity(String city);
}

// Custom repository implementation
@Component
public class UserDocumentRepositoryImpl {
    
    private final MongoTemplate mongoTemplate;
    
    public UserDocumentRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }
    
    public List<UserDocument> findUsersWithComplexCriteria(UserSearchCriteria criteria) {
        Criteria combinedCriteria = new Criteria();
        List<Criteria> criteriaList = new ArrayList<>();
        
        if (criteria.getEmail() != null) {
            criteriaList.add(Criteria.where("email").regex(criteria.getEmail(), "i"));
        }
        
        if (criteria.getDepartment() != null) {
            criteriaList.add(Criteria.where("department").is(criteria.getDepartment()));
        }
        
        if (criteria.getActive() != null) {
            criteriaList.add(Criteria.where("active").is(criteria.getActive()));
        }
        
        if (!criteriaList.isEmpty()) {
            combinedCriteria.andOperator(criteriaList.toArray(new Criteria[0]));
        }
        
        Query query = new Query(combinedCriteria);
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        
        return mongoTemplate.find(query, UserDocument.class);
    }
    
    public AggregationResults<DepartmentStats> getDepartmentStatistics() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("active").is(true)),
                Aggregation.group("department")
                        .count().as("userCount")
                        .avg("metadata.salary").as("avgSalary")
                        .max("createdAt").as("lastHired"),
                Aggregation.sort(Sort.Direction.DESC, "userCount")
        );
        
        return mongoTemplate.aggregate(aggregation, "users", DepartmentStats.class);
    }
}
```

## Redis Integration

### Configuration

```yaml
# application.yml - Redis Configuration
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: ${REDIS_DATABASE:0}
    timeout: ${REDIS_TIMEOUT:2000ms}
    
    # Lettuce connection pool
    lettuce:
      pool:
        max-active: ${REDIS_POOL_MAX_ACTIVE:10}
        max-idle: ${REDIS_POOL_MAX_IDLE:10}
        min-idle: ${REDIS_POOL_MIN_IDLE:2}
        max-wait: ${REDIS_POOL_MAX_WAIT:1000ms}
      shutdown-timeout: 200ms
      
    # Sentinel configuration (if using Redis Sentinel)
    sentinel:
      master: ${REDIS_SENTINEL_MASTER:mymaster}
      nodes: ${REDIS_SENTINEL_NODES:localhost:26379}
      password: ${REDIS_SENTINEL_PASSWORD:}
      
    # Cluster configuration (if using Redis Cluster)
    cluster:
      nodes: ${REDIS_CLUSTER_NODES:localhost:7000,localhost:7001,localhost:7002}
      max-redirects: 3
      
  # Cache configuration
  cache:
    type: redis
    cache-names: users,products,departments
    redis:
      time-to-live: ${CACHE_TTL:3600000} # 1 hour
      cache-null-values: false
      key-prefix: "springboost:"
      use-key-prefix: true
```

### Cache Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
        
        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                "users", defaultConfig.entryTtl(Duration.ofMinutes(30)),
                "products", defaultConfig.entryTtl(Duration.ofHours(2)),
                "departments", defaultConfig.entryTtl(Duration.ofHours(6)),
                "user-sessions", defaultConfig.entryTtl(Duration.ofMinutes(15))
        );
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Key serialization
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value serialization
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, 
                ObjectMapper.DefaultTyping.NON_FINAL);
        serializer.setObjectMapper(objectMapper);
        
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        RedisSerializationContext<String, Object> serializationContext = 
                RedisSerializationContext.<String, Object>newSerializationContext()
                        .key(StringRedisSerializer.UTF_8)
                        .value(new GenericJackson2JsonRedisSerializer())
                        .hashKey(StringRedisSerializer.UTF_8)
                        .hashValue(new GenericJackson2JsonRedisSerializer())
                        .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
```

### Service with Caching

```java
@Service
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public UserService(UserRepository userRepository, RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }
    
    @Cacheable(value = "users", key = "#userId")
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }
    
    @Cacheable(value = "users", key = "#email")
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    @CacheEvict(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }
    
    @CacheEvict(value = "users", allEntries = true)
    public void clearUserCache() {
        // Method implementation will trigger cache eviction
    }
    
    // Custom Redis operations
    public void setUserSession(String sessionId, UserSession session) {
        redisTemplate.opsForValue().set(
                "session:" + sessionId, 
                session, 
                Duration.ofMinutes(30)
        );
    }
    
    public Optional<UserSession> getUserSession(String sessionId) {
        UserSession session = (UserSession) redisTemplate.opsForValue()
                .get("session:" + sessionId);
        return Optional.ofNullable(session);
    }
    
    public void incrementUserLoginCount(Long userId) {
        String key = "login_count:" + userId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofDays(30));
    }
    
    // Pub/Sub messaging
    public void publishUserEvent(UserEvent event) {
        redisTemplate.convertAndSend("user-events", event);
    }
}

@Component
public class UserEventListener {
    
    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        // Handle user creation event
        log.info("User created: {}", event.getUser().getEmail());
    }
    
    @RedisMessageListener(topic = "user-events")
    public void handleUserEvent(UserEvent event) {
        // Handle Redis pub/sub messages
        log.info("Received user event: {}", event);
    }
}
```

## Multi-Database Configuration

### Multiple DataSources

```java
@Configuration
public class DatabaseConfig {
    
    // Primary database (PostgreSQL)
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary.hikari")
    public DataSource primaryDataSource() {
        return primaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
    
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(primaryDataSource())
                .packages("com.springboost.entity.primary")
                .persistenceUnit("primary")
                .properties(hibernateProperties())
                .build();
    }
    
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
    
    // Secondary database (MySQL)
    @Bean
    @ConfigurationProperties("spring.datasource.secondary")
    public DataSourceProperties secondaryDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary.hikari")
    public DataSource secondaryDataSource() {
        return secondaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(secondaryDataSource())
                .packages("com.springboost.entity.secondary")
                .persistenceUnit("secondary")
                .properties(hibernateProperties())
                .build();
    }
    
    @Bean
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
    
    private Map<String, Object> hibernateProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQL15Dialect");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", false);
        return properties;
    }
}

# application.yml for multiple databases
spring:
  datasource:
    primary:
      url: jdbc:postgresql://localhost:5432/springboost_primary
      username: postgres
      password: password
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
    secondary:
      url: jdbc:mysql://localhost:3306/springboost_secondary
      username: mysql
      password: password
      driver-class-name: com.mysql.cj.jdbc.Driver
      hikari:
        maximum-pool-size: 15
        minimum-idle: 3
```

This comprehensive database integration guide provides production-ready patterns for integrating Spring Boot applications with various database technologies, ensuring optimal performance, security, and maintainability.
