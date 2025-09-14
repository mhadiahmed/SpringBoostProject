# Spring Data 2.x Specific Guidelines

## Overview

Spring Data 2.x guidelines for JPA repositories, query methods, and data access patterns. This version is commonly used with Spring Boot 2.x applications and Java 8-17.

## Repository Interfaces

### Basic Repository Configuration

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Query methods with Spring Data 2.x syntax
    Optional<User> findByEmail(String email);
    List<User> findByFirstNameContainingIgnoreCase(String firstName);
    List<User> findByActiveTrue();
    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Derived count queries
    long countByActiveTrue();
    boolean existsByEmail(String email);
    
    // Limiting results
    User findFirstByOrderByCreatedAtDesc();
    List<User> findTop10ByOrderByCreatedAtDesc();
    
    // Sorting and pagination
    List<User> findByActiveTrueOrderByLastNameAsc();
    Page<User> findByDepartment(String department, Pageable pageable);
    Slice<User> findByActiveTrue(Pageable pageable);
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Complex property navigation
    List<Product> findByCategoryNameAndPriceLessThan(String categoryName, BigDecimal price);
    
    // Collection queries
    List<Product> findByTagsContaining(String tag);
    List<Product> findByReviewsRatingGreaterThan(Double rating);
    
    // Null handling
    List<Product> findByDescriptionIsNull();
    List<Product> findByDescriptionIsNotNull();
    
    // String queries
    List<Product> findByNameStartingWith(String prefix);
    List<Product> findByNameEndingWith(String suffix);
    List<Product> findByNameContaining(String keyword);
}
```

### Custom Repository Implementation

```java
public interface UserRepositoryCustom {
    List<User> findUsersWithComplexCriteria(UserSearchCriteria criteria);
    Page<User> searchUsers(String searchTerm, Pageable pageable);
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
            List<Order> orders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                if (order.isAscending()) {
                    orders.add(cb.asc(dataRoot.get(order.getProperty())));
                } else {
                    orders.add(cb.desc(dataRoot.get(order.getProperty())));
                }
            }
            dataQuery.orderBy(orders);
        }
        
        List<User> content = entityManager.createQuery(dataQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        
        return new PageImpl<>(content, pageable, total);
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

## Custom Queries

### @Query Annotation

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // JPQL queries
    @Query("SELECT u FROM User u WHERE u.email = ?1")
    Optional<User> findByEmailJpql(String email);
    
    @Query("SELECT u FROM User u WHERE u.firstName LIKE %:name% OR u.lastName LIKE %:name%")
    List<User> findByNameContaining(@Param("name") String name);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    List<User> findByRoleName(@Param("roleName") String roleName);
    
    // Native SQL queries
    @Query(value = "SELECT * FROM users u WHERE u.created_at > :date", nativeQuery = true)
    List<User> findUsersCreatedAfter(@Param("date") LocalDateTime date);
    
    @Query(value = """
            SELECT u.*, COUNT(p.id) as project_count 
            FROM users u 
            LEFT JOIN user_projects up ON u.id = up.user_id 
            LEFT JOIN projects p ON up.project_id = p.id 
            WHERE u.active = true 
            GROUP BY u.id 
            HAVING COUNT(p.id) > :minProjects
            """, nativeQuery = true)
    List<Object[]> findActiveUsersWithMinProjects(@Param("minProjects") int minProjects);
    
    // Modifying queries
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :loginTime WHERE u.email = :email")
    @Transactional
    int updateLastLogin(@Param("email") String email, @Param("loginTime") LocalDateTime loginTime);
    
    @Modifying
    @Query("DELETE FROM User u WHERE u.active = false AND u.lastLogin < :cutoffDate")
    @Transactional
    int deleteInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Projection queries
    @Query("SELECT u.firstName, u.lastName, u.email FROM User u WHERE u.department = :dept")
    List<Object[]> findUserSummaryByDepartment(@Param("dept") String department);
    
    // DTO projection
    @Query("SELECT new com.example.dto.UserSummaryDto(u.id, u.firstName, u.lastName, u.email) " +
           "FROM User u WHERE u.active = true")
    List<UserSummaryDto> findActiveUserSummaries();
    
    // Pagination with custom query
    @Query("SELECT u FROM User u WHERE u.department = :dept ORDER BY u.lastName")
    Page<User> findByDepartmentCustom(@Param("dept") String department, Pageable pageable);
}
```

### Named Queries

```java
@Entity
@Table(name = "users")
@NamedQueries({
    @NamedQuery(
        name = "User.findByActiveStatus",
        query = "SELECT u FROM User u WHERE u.active = :active ORDER BY u.lastName"
    ),
    @NamedQuery(
        name = "User.findByDepartmentWithRoles",
        query = "SELECT u FROM User u JOIN FETCH u.roles WHERE u.department = :department"
    )
})
public class User {
    // Entity definition
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Named query methods (Spring Data 2.x automatically detects them)
    List<User> findByActiveStatus(@Param("active") boolean active);
    List<User> findByDepartmentWithRoles(@Param("department") String department);
}
```

## Projections

### Interface-based Projections

```java
// Simple interface projection
public interface UserNameProjection {
    String getFirstName();
    String getLastName();
    
    // SpEL expression
    @Value("#{target.firstName + ' ' + target.lastName}")
    String getFullName();
}

// Nested projection
public interface UserWithDepartmentProjection {
    String getFirstName();
    String getLastName();
    DepartmentProjection getDepartment();
    
    interface DepartmentProjection {
        String getName();
        String getLocation();
    }
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    List<UserNameProjection> findByDepartment(String department);
    
    @Query("SELECT u FROM User u JOIN FETCH u.department WHERE u.active = true")
    List<UserWithDepartmentProjection> findActiveUsersWithDepartment();
}
```

### Class-based Projections (DTOs)

```java
public class UserSummaryDto {
    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String department;
    
    public UserSummaryDto(Long id, String firstName, String lastName, 
                         String email, String department) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.department = department;
    }
    
    // Getters
    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    @Query("SELECT new com.example.dto.UserSummaryDto(u.id, u.firstName, u.lastName, u.email, u.department) " +
           "FROM User u WHERE u.active = true")
    List<UserSummaryDto> findActiveUserSummaries();
    
    @Query("SELECT new com.example.dto.UserSummaryDto(u.id, u.firstName, u.lastName, u.email, d.name) " +
           "FROM User u JOIN u.department d WHERE d.active = true")
    Page<UserSummaryDto> findUsersInActiveDepartments(Pageable pageable);
}
```

## Auditing

### JPA Auditing Configuration

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware();
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

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    // Getters and setters
}

@Entity
@Table(name = "users")
public class User extends AuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    // Other fields and methods
}
```

## Specifications

### JPA Specifications for Dynamic Queries

```java
public class UserSpecifications {
    
    public static Specification<User> hasEmail(String email) {
        return (root, query, criteriaBuilder) -> {
            if (email == null || email.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("email")), 
                    "%" + email.toLowerCase() + "%"
            );
        };
    }
    
    public static Specification<User> hasFirstName(String firstName) {
        return (root, query, criteriaBuilder) -> {
            if (firstName == null || firstName.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("firstName")), 
                    "%" + firstName.toLowerCase() + "%"
            );
        };
    }
    
    public static Specification<User> isActive(Boolean active) {
        return (root, query, criteriaBuilder) -> {
            if (active == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("active"), active);
        };
    }
    
    public static Specification<User> inDepartment(String department) {
        return (root, query, criteriaBuilder) -> {
            if (department == null || department.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("department"), department);
        };
    }
    
    public static Specification<User> createdBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, criteriaBuilder) -> {
            if (start == null && end == null) {
                return criteriaBuilder.conjunction();
            }
            if (start != null && end != null) {
                return criteriaBuilder.between(root.get("createdAt"), start, end);
            }
            if (start != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), start);
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), end);
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
}

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
        Specification<User> spec = Specification.where(null);
        
        spec = spec.and(UserSpecifications.hasEmail(criteria.getEmail()));
        spec = spec.and(UserSpecifications.hasFirstName(criteria.getFirstName()));
        spec = spec.and(UserSpecifications.isActive(criteria.getActive()));
        spec = spec.and(UserSpecifications.inDepartment(criteria.getDepartment()));
        spec = spec.and(UserSpecifications.createdBetween(criteria.getStartDate(), criteria.getEndDate()));
        
        if (criteria.getRoleName() != null) {
            spec = spec.and(UserSpecifications.hasRole(criteria.getRoleName()));
        }
        
        return userRepository.findAll(spec, pageable);
    }
    
    public List<User> findUsersWithComplexCriteria(String searchTerm, boolean activeOnly) {
        Specification<User> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (activeOnly) {
                predicates.add(criteriaBuilder.isTrue(root.get("active")));
            }
            
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                String likePattern = "%" + searchTerm.toLowerCase() + "%";
                Predicate emailPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("email")), likePattern);
                Predicate firstNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("firstName")), likePattern);
                Predicate lastNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("lastName")), likePattern);
                
                predicates.add(criteriaBuilder.or(emailPredicate, firstNamePredicate, lastNamePredicate));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        
        Sort sort = Sort.by(Sort.Direction.ASC, "lastName", "firstName");
        return userRepository.findAll(spec, sort);
    }
}
```

## Query by Example

### QBE for Dynamic Queries

```java
@Service
@Transactional
public class UserQueryByExampleService {
    
    private final UserRepository userRepository;
    
    public UserQueryByExampleService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public List<User> findUsersByExample(User probe) {
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withIgnoreCase()
                .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                .withIgnoreNullValues()
                .withIgnorePaths("id", "createdAt", "updatedAt", "version");
        
        Example<User> example = Example.of(probe, matcher);
        return userRepository.findAll(example);
    }
    
    public Page<User> findUsersByExample(User probe, Pageable pageable) {
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withIgnoreCase()
                .withStringMatcher(ExampleMatcher.StringMatcher.STARTING)
                .withIgnoreNullValues()
                .withIgnorePaths("id", "password", "createdAt", "updatedAt");
        
        Example<User> example = Example.of(probe, matcher);
        return userRepository.findAll(example, pageable);
    }
    
    public Optional<User> findUserByEmailExample(String email) {
        User probe = new User();
        probe.setEmail(email);
        
        ExampleMatcher matcher = ExampleMatcher.matching()
                .withIgnoreCase()
                .withIgnorePaths("id", "firstName", "lastName", "createdAt", "updatedAt");
        
        Example<User> example = Example.of(probe, matcher);
        return userRepository.findOne(example);
    }
}
```

## Transaction Management

### Declarative Transactions

```java
@Service
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final EmailService emailService;
    
    public UserService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }
    
    @Transactional(readOnly = true)
    public List<User> getAllActiveUsers() {
        return userRepository.findByActiveTrue();
    }
    
    @Transactional(readOnly = true)
    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    
    @Transactional
    public User createUser(CreateUserRequest request) {
        // Check if user already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email already exists");
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .active(true)
                .build();
        
        User savedUser = userRepository.save(user);
        
        // Send welcome email (this should use @Async for non-critical operations)
        emailService.sendWelcomeEmail(savedUser);
        
        return savedUser;
    }
    
    @Transactional
    public User updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setDepartment(request.getDepartment());
        
        return userRepository.save(user);
    }
    
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        // Soft delete
        user.setActive(false);
        userRepository.save(user);
        
        // Or hard delete
        // userRepository.delete(user);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processUserBatch(List<CreateUserRequest> requests) {
        for (CreateUserRequest request : requests) {
            try {
                createUser(request);
            } catch (Exception e) {
                // Log error but continue processing other users
                log.error("Failed to create user: {}", request.getEmail(), e);
            }
        }
    }
    
    @Transactional(rollbackFor = {Exception.class})
    public void updateUserWithRollback(Long userId, UpdateUserRequest request) {
        updateUser(userId, request);
        
        // If this operation fails, the entire transaction will rollback
        performSomeRiskyOperation();
    }
    
    @Transactional(noRollbackFor = {ValidationException.class})
    public void updateUserWithPartialRollback(Long userId, UpdateUserRequest request) {
        updateUser(userId, request);
        
        // If this throws ValidationException, transaction won't rollback
        validateUserData(request);
    }
}
```

## Entity Relationships

### JPA Relationships in Spring Data 2.x

```java
@Entity
@Table(name = "users")
public class User extends AuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    // Many-to-One
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    
    // One-to-Many
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Address> addresses = new ArrayList<>();
    
    // Many-to-Many
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
    
    // One-to-One
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile profile;
    
    // Helper methods for bidirectional relationships
    public void addAddress(Address address) {
        addresses.add(address);
        address.setUser(this);
    }
    
    public void removeAddress(Address address) {
        addresses.remove(address);
        address.setUser(null);
    }
    
    public void addRole(Role role) {
        roles.add(role);
        role.getUsers().add(this);
    }
    
    public void removeRole(Role role) {
        roles.remove(role);
        role.getUsers().remove(this);
    }
}

@Entity
@Table(name = "departments")
public class Department {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @OneToMany(mappedBy = "department")
    private List<User> users = new ArrayList<>();
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Join fetch to avoid N+1 queries
    @Query("SELECT u FROM User u JOIN FETCH u.department WHERE u.active = true")
    List<User> findActiveUsersWithDepartment();
    
    @Query("SELECT u FROM User u JOIN FETCH u.roles WHERE u.id = :userId")
    Optional<User> findByIdWithRoles(@Param("userId") Long userId);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.addresses WHERE u.email = :email")
    Optional<User> findByEmailWithAddresses(@Param("email") String email);
    
    // Projection to avoid loading unnecessary data
    @Query("SELECT u.id, u.firstName, u.lastName, d.name as departmentName " +
           "FROM User u JOIN u.department d WHERE u.active = true")
    List<Object[]> findActiveUserSummaries();
}
```

Spring Data 2.x provides robust data access capabilities with JPA repositories, custom queries, projections, and specifications. These patterns work well with Spring Boot 2.x applications and provide a solid foundation for data layer development.
