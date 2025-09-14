# Spring Data 3.x Specific Guidelines

## Overview

Spring Data 3.x guidelines for modern JPA repositories, enhanced query methods, and Jakarta EE integration. This version is used with Spring Boot 3.x applications and requires Java 17+.

## Repository Interfaces

### Enhanced Repository Configuration

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Enhanced query methods with Spring Data 3.x
    Optional<User> findByEmail(String email);
    List<User> findByFirstNameContainingIgnoreCase(String firstName);
    List<User> findByActiveTrue();
    List<User> findByCreatedAtBetween(Instant start, Instant end);
    
    // Scrolling (new in Spring Data 3.1)
    Window<User> findTop10ByOrderByCreatedAtDesc();
    Window<User> findByDepartment(String department, ScrollPosition position);
    
    // Derived count and exists queries
    long countByActiveTrue();
    boolean existsByEmail(String email);
    
    // Limiting results with new syntax
    Optional<User> findFirstByOrderByCreatedAtDesc();
    List<User> findTop10ByActiveTrueOrderByLastNameAsc();
    
    // Enhanced pagination and sorting
    Page<User> findByDepartment(String department, Pageable pageable);
    Slice<User> findByActiveTrue(Pageable pageable);
    
    // Stream processing for large datasets
    @Query("SELECT u FROM User u WHERE u.active = true")
    Stream<User> streamActiveUsers();
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Complex property navigation
    List<Product> findByCategoryNameAndPriceLessThan(String categoryName, BigDecimal price);
    
    // Collection queries
    List<Product> findByTagsContaining(String tag);
    List<Product> findByReviewsRatingGreaterThan(Double rating);
    
    // Enhanced null handling
    List<Product> findByDescriptionIsNull();
    List<Product> findByDescriptionIsNotNull();
    List<Product> findByDescriptionEmpty();
    List<Product> findByDescriptionNotEmpty();
    
    // Advanced string queries
    List<Product> findByNameStartingWith(String prefix);
    List<Product> findByNameEndingWith(String suffix);
    List<Product> findByNameContaining(String keyword);
    List<Product> findByNameMatches(String regex);
}
```

### Custom Repository Implementation

```java
public interface UserRepositoryCustom {
    List<User> findUsersWithComplexCriteria(UserSearchCriteria criteria);
    Page<User> searchUsers(String searchTerm, Pageable pageable);
    Window<User> scrollUsers(String searchTerm, ScrollPosition position);
}

@Repository
public class UserRepositoryImpl implements UserRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<User> findUsersWithComplexCriteria(UserSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> user = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getEmail() != null) {
            predicates.add(cb.like(cb.lower(user.get("email")), 
                    "%" + criteria.getEmail().toLowerCase() + "%"));
        }
        
        if (criteria.getMinAge() != null) {
            predicates.add(cb.greaterThanOrEqualTo(user.get("age"), criteria.getMinAge()));
        }
        
        if (criteria.getDepartments() != null && !criteria.getDepartments().isEmpty()) {
            predicates.add(user.get("department").in(criteria.getDepartments()));
        }
        
        // Jakarta validation integration
        if (criteria.getCreatedAfter() != null) {
            predicates.add(cb.greaterThanOrEqualTo(user.get("createdAt"), criteria.getCreatedAfter()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.asc(user.get("lastName")), cb.asc(user.get("firstName")));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    public Page<User> searchUsers(String searchTerm, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);
        countQuery.select(cb.count(countRoot));
        
        Predicate searchPredicate = createSearchPredicate(cb, countRoot, searchTerm);
        countQuery.where(searchPredicate);
        
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        // Data query
        CriteriaQuery<User> dataQuery = cb.createQuery(User.class);
        Root<User> dataRoot = dataQuery.from(User.class);
        dataQuery.where(createSearchPredicate(cb, dataRoot, searchTerm));
        
        // Apply sorting
        if (pageable.getSort().isSorted()) {
            List<Order> orders = pageable.getSort().stream()
                    .map(order -> order.isAscending() 
                            ? cb.asc(dataRoot.get(order.getProperty()))
                            : cb.desc(dataRoot.get(order.getProperty())))
                    .toList();
            dataQuery.orderBy(orders);
        }
        
        List<User> content = entityManager.createQuery(dataQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        
        return new PageImpl<>(content, pageable, total);
    }
    
    @Override
    public Window<User> scrollUsers(String searchTerm, ScrollPosition position) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.where(createSearchPredicate(cb, root, searchTerm));
        query.orderBy(cb.asc(root.get("id")));
        
        TypedQuery<User> typedQuery = entityManager.createQuery(query);
        
        // Handle scroll position
        if (position instanceof OffsetScrollPosition offsetPosition) {
            typedQuery.setFirstResult((int) offsetPosition.getOffset());
        } else if (position instanceof KeysetScrollPosition keysetPosition) {
            // Handle keyset scrolling for better performance
            Object lastId = keysetPosition.getKeys().get("id");
            if (lastId != null) {
                query.where(cb.and(
                        createSearchPredicate(cb, root, searchTerm),
                        cb.greaterThan(root.get("id"), (Long) lastId)
                ));
            }
        }
        
        typedQuery.setMaxResults(20); // Page size
        List<User> content = typedQuery.getResultList();
        
        return Window.from(content, position -> ScrollPosition.offset(position));
    }
    
    private Predicate createSearchPredicate(CriteriaBuilder cb, Root<User> root, String searchTerm) {
        String likePattern = "%" + searchTerm.toLowerCase() + "%";
        return cb.or(
                cb.like(cb.lower(root.get("firstName")), likePattern),
                cb.like(cb.lower(root.get("lastName")), likePattern),
                cb.like(cb.lower(root.get("email")), likePattern)
        );
    }
}

// Extend both interfaces
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    // Standard repository methods
}
```

## Enhanced Queries

### @Query with Modern Features

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // JPQL queries with Jakarta namespace
    @Query("SELECT u FROM User u WHERE u.email = ?1")
    Optional<User> findByEmailJpql(String email);
    
    @Query("SELECT u FROM User u WHERE u.firstName LIKE %:name% OR u.lastName LIKE %:name%")
    List<User> findByNameContaining(@Param("name") String name);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    List<User> findByRoleName(@Param("roleName") String roleName);
    
    // Enhanced native SQL queries
    @Query(value = "SELECT * FROM users u WHERE u.created_at > :date", nativeQuery = true)
    List<User> findUsersCreatedAfter(@Param("date") Instant date);
    
    @Query(value = """
            SELECT u.*, COUNT(p.id) as project_count 
            FROM users u 
            LEFT JOIN user_projects up ON u.id = up.user_id 
            LEFT JOIN projects p ON up.project_id = p.id 
            WHERE u.active = true 
            GROUP BY u.id 
            HAVING COUNT(p.id) > :minProjects
            ORDER BY COUNT(p.id) DESC
            """, nativeQuery = true)
    List<Object[]> findActiveUsersWithMinProjects(@Param("minProjects") int minProjects);
    
    // Modifying queries with enhanced transaction support
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :loginTime WHERE u.email = :email")
    int updateLastLogin(@Param("email") String email, @Param("loginTime") Instant loginTime);
    
    @Modifying
    @Query("DELETE FROM User u WHERE u.active = false AND u.lastLogin < :cutoffDate")
    int deleteInactiveUsers(@Param("cutoffDate") Instant cutoffDate);
    
    // Batch operations
    @Modifying
    @Query("UPDATE User u SET u.department = :newDept WHERE u.department = :oldDept")
    int transferUsersBetweenDepartments(@Param("oldDept") String oldDept, @Param("newDept") String newDept);
    
    // Enhanced projection queries
    @Query("SELECT u.firstName, u.lastName, u.email FROM User u WHERE u.department = :dept")
    List<Object[]> findUserSummaryByDepartment(@Param("dept") String department);
    
    // Modern DTO projection with record classes
    @Query("""
           SELECT new com.example.dto.UserSummaryRecord(
               u.id, u.firstName, u.lastName, u.email, u.department, u.createdAt
           ) 
           FROM User u WHERE u.active = true
           """)
    List<UserSummaryRecord> findActiveUserSummaries();
    
    // Scrolling queries
    @Query("SELECT u FROM User u WHERE u.department = :dept ORDER BY u.id")
    Window<User> scrollByDepartment(@Param("dept") String department, ScrollPosition position);
    
    // Stream processing
    @Query("SELECT u FROM User u WHERE u.active = true")
    Stream<User> streamActiveUsers();
    
    // Complex aggregation queries
    @Query("""
           SELECT u.department, COUNT(u), AVG(u.age), MAX(u.createdAt)
           FROM User u 
           WHERE u.active = true 
           GROUP BY u.department 
           ORDER BY COUNT(u) DESC
           """)
    List<Object[]> getDepartmentStatistics();
}
```

### Record-based DTOs

```java
// Modern record for projection (Java 17+)
public record UserSummaryRecord(
        Long id,
        String firstName,
        String lastName,
        String email,
        String department,
        Instant createdAt
) {}

public record DepartmentStatsRecord(
        String departmentName,
        Long userCount,
        Double averageAge,
        Instant lastUserAdded
) {}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    @Query("""
           SELECT new com.example.dto.UserSummaryRecord(
               u.id, u.firstName, u.lastName, u.email, u.department, u.createdAt
           )
           FROM User u WHERE u.active = true
           """)
    List<UserSummaryRecord> findActiveUserSummaries();
    
    @Query("""
           SELECT new com.example.dto.DepartmentStatsRecord(
               u.department, COUNT(u), AVG(u.age), MAX(u.createdAt)
           )
           FROM User u 
           WHERE u.active = true 
           GROUP BY u.department
           """)
    List<DepartmentStatsRecord> getDepartmentStatistics();
}
```

## Enhanced Projections

### Interface-based Projections with Computed Properties

```java
// Enhanced interface projection
public interface UserProjection {
    Long getId();
    String getFirstName();
    String getLastName();
    String getEmail();
    String getDepartment();
    Instant getCreatedAt();
    
    // SpEL expressions with enhanced capabilities
    @Value("#{target.firstName + ' ' + target.lastName}")
    String getFullName();
    
    @Value("#{T(java.time.Period).between(target.birthDate, T(java.time.LocalDate).now()).years}")
    Integer getAge();
    
    @Value("#{target.email.substring(target.email.indexOf('@') + 1)}")
    String getEmailDomain();
    
    // Nested projections
    DepartmentProjection getDepartmentInfo();
    List<RoleProjection> getRoles();
    
    interface DepartmentProjection {
        String getName();
        String getLocation();
        ManagerProjection getManager();
        
        interface ManagerProjection {
            String getFirstName();
            String getLastName();
            String getEmail();
        }
    }
    
    interface RoleProjection {
        String getName();
        String getDescription();
        Set<PermissionProjection> getPermissions();
        
        interface PermissionProjection {
            String getName();
            String getResource();
            String getAction();
        }
    }
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    @Query("""
           SELECT u FROM User u 
           LEFT JOIN FETCH u.departmentInfo 
           LEFT JOIN FETCH u.departmentInfo.manager 
           WHERE u.active = true
           """)
    List<UserProjection> findActiveUsersWithDetails();
    
    @Query("""
           SELECT u FROM User u 
           LEFT JOIN FETCH u.roles r 
           LEFT JOIN FETCH r.permissions 
           WHERE u.email = :email
           """)
    Optional<UserProjection> findByEmailWithRoles(@Param("email") String email);
}
```

## Enhanced Auditing

### Modern JPA Auditing Configuration

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
public class JpaAuditingConfig {
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }
    
    @Bean
    public DateTimeProvider dateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}

public class SpringSecurityAuditorAware implements AuditorAware<String> {
    
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of("system");
        }
        
        return Optional.of(authentication.getName());
    }
}

// Enhanced auditable entity with Jakarta annotations
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    // Enhanced getters and setters
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }
    
    // Utility methods
    public boolean isNew() {
        return createdAt == null;
    }
    
    public Duration getAge() {
        return createdAt != null ? Duration.between(createdAt, Instant.now()) : Duration.ZERO;
    }
}

// Entity with enhanced auditing
@Entity
@Table(name = "users")
public class User extends AuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    // Jakarta validation
    @Column(name = "birth_date")
    private LocalDate birthDate;
    
    @Column(nullable = false)
    private Boolean active = true;
    
    // Other fields and relationships
}
```

## Enhanced Specifications

### Type-safe Specifications with Modern Java

```java
public class UserSpecifications {
    
    // Type-safe field references
    public static final String EMAIL = "email";
    public static final String FIRST_NAME = "firstName";
    public static final String LAST_NAME = "lastName";
    public static final String DEPARTMENT = "department";
    public static final String CREATED_AT = "createdAt";
    public static final String ACTIVE = "active";
    
    public static Specification<User> hasEmail(String email) {
        return (root, query, criteriaBuilder) -> {
            if (email == null || email.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get(EMAIL)), 
                    "%" + email.toLowerCase() + "%"
            );
        };
    }
    
    public static Specification<User> hasName(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            String likePattern = "%" + name.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FIRST_NAME)), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(LAST_NAME)), likePattern)
            );
        };
    }
    
    public static Specification<User> isActive(Boolean active) {
        return (root, query, criteriaBuilder) -> {
            if (active == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get(ACTIVE), active);
        };
    }
    
    public static Specification<User> inDepartment(String department) {
        return (root, query, criteriaBuilder) -> {
            if (department == null || department.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get(DEPARTMENT), department);
        };
    }
    
    public static Specification<User> createdBetween(Instant start, Instant end) {
        return (root, query, criteriaBuilder) -> {
            if (start == null && end == null) {
                return criteriaBuilder.conjunction();
            }
            if (start != null && end != null) {
                return criteriaBuilder.between(root.get(CREATED_AT), start, end);
            }
            if (start != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get(CREATED_AT), start);
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get(CREATED_AT), end);
        };
    }
    
    public static Specification<User> hasRole(String roleName) {
        return (root, query, criteriaBuilder) -> {
            if (roleName == null || roleName.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            Join<User, Role> roleJoin = root.join("roles");
            return criteriaBuilder.equal(roleJoin.get("name"), roleName);
        };
    }
    
    // Enhanced specifications with modern patterns
    public static Specification<User> searchText(String searchText) {
        return (root, query, criteriaBuilder) -> {
            if (searchText == null || searchText.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            
            String likePattern = "%" + searchText.toLowerCase() + "%";
            return criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(EMAIL)), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(FIRST_NAME)), likePattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get(LAST_NAME)), likePattern)
            );
        };
    }
    
    public static Specification<User> withComplex(UserSearchCriteria criteria) {
        return Specification
                .where(hasEmail(criteria.email()))
                .and(hasName(criteria.name()))
                .and(isActive(criteria.active()))
                .and(inDepartment(criteria.department()))
                .and(createdBetween(criteria.startDate(), criteria.endDate()))
                .and(criteria.roleName() != null ? hasRole(criteria.roleName()) : null);
    }
}

// Record for search criteria
public record UserSearchCriteria(
        String email,
        String name,
        Boolean active,
        String department,
        String roleName,
        Instant startDate,
        Instant endDate
) {}

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    // Standard repository methods
}

@Service
@Transactional
public class UserSearchService {
    
    private final UserRepository userRepository;
    
    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public Page<User> searchUsers(UserSearchCriteria criteria, Pageable pageable) {
        Specification<User> spec = UserSpecifications.withComplex(criteria);
        return userRepository.findAll(spec, pageable);
    }
    
    public Window<User> scrollUsers(UserSearchCriteria criteria, ScrollPosition position) {
        Specification<User> spec = UserSpecifications.withComplex(criteria);
        
        // For scrolling, we need to ensure ordering
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        Pageable pageable = PageRequest.of(0, 20, sort);
        
        // This would need custom implementation for true scrolling
        Page<User> page = userRepository.findAll(spec, pageable);
        return Window.from(page.getContent(), position -> ScrollPosition.offset(position));
    }
}
```

## Enhanced Transaction Management

### Modern Transaction Patterns

```java
@Service
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    
    public UserService(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    
    @Transactional
    public User createUser(CreateUserRequest request) {
        // Validation
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("User with email already exists");
        }
        
        User user = User.builder()
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .active(true)
                .build();
        
        User savedUser = userRepository.save(user);
        
        // Asynchronous notification (separate transaction)
        notificationService.sendWelcomeNotification(savedUser);
        
        return savedUser;
    }
    
    @Transactional
    public User updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setDepartment(request.department());
        
        return userRepository.save(user);
    }
    
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        user.setActive(false);
        userRepository.save(user);
    }
    
    // Batch processing with proper transaction management
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<User> createUsersBatch(List<CreateUserRequest> requests) {
        return requests.stream()
                .map(this::createUserSafely)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
    
    private Optional<User> createUserSafely(CreateUserRequest request) {
        try {
            return Optional.of(createUser(request));
        } catch (Exception e) {
            log.error("Failed to create user: {}", request.email(), e);
            return Optional.empty();
        }
    }
    
    // Stream processing with proper resource management
    @Transactional(readOnly = true)
    public void processAllActiveUsers(Consumer<User> processor) {
        try (Stream<User> userStream = userRepository.streamActiveUsers()) {
            userStream.forEach(processor);
        }
    }
}

// Record-based DTOs
public record CreateUserRequest(
        String email,
        String firstName,
        String lastName,
        LocalDate birthDate
) {}

public record UpdateUserRequest(
        String firstName,
        String lastName,
        String department
) {}
```

Spring Data 3.x provides enhanced capabilities with Jakarta EE integration, modern Java features, record-based DTOs, improved query methods, and better performance optimizations. These patterns work seamlessly with Spring Boot 3.x applications and leverage Java 17+ features for cleaner, more maintainable code.
