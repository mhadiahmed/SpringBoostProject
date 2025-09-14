# Spring Data Guidelines

## Overview

Comprehensive guidelines for Spring Data JPA, focusing on repository patterns, query methods, transaction management, caching strategies, and database migration patterns.

## Repository Patterns

### Basic Repository Interface

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Query method derivation
    Optional<User> findByEmail(String email);
    
    Optional<User> findByEmailAndActiveTrue(String email);
    
    List<User> findByLastNameContainingIgnoreCase(String lastName);
    
    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<User> findByRolesNameIn(Collection<String> roleNames);
    
    // Existence checks
    boolean existsByEmail(String email);
    
    // Count queries
    long countByActiveTrue();
    
    // Delete operations
    void deleteByIdAndActiveTrue(Long id);
    
    @Modifying
    @Query("UPDATE User u SET u.active = false WHERE u.id = :id")
    int softDeleteById(@Param("id") Long id);
}
```

### Custom Repository Implementation

```java
// Custom repository interface
public interface UserRepositoryCustom {
    List<User> findUsersByComplexCriteria(UserSearchCriteria criteria);
    Page<User> findUsersWithDynamicFiltering(UserFilter filter, Pageable pageable);
}

// Implementation
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {
    
    private final EntityManager entityManager;
    
    @Override
    public List<User> findUsersByComplexCriteria(UserSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (StringUtils.hasText(criteria.getEmail())) {
            predicates.add(cb.like(
                cb.lower(root.get("email")), 
                "%" + criteria.getEmail().toLowerCase() + "%"
            ));
        }
        
        if (criteria.getMinAge() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get("age"), criteria.getMinAge()
            ));
        }
        
        if (criteria.getDepartments() != null && !criteria.getDepartments().isEmpty()) {
            predicates.add(root.get("department").in(criteria.getDepartments()));
        }
        
        if (criteria.isActiveOnly()) {
            predicates.add(cb.isTrue(root.get("active")));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.asc(root.get("lastName")), cb.asc(root.get("firstName")));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    public Page<User> findUsersWithDynamicFiltering(UserFilter filter, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = buildPredicates(filter, cb, root);
        query.where(predicates.toArray(new Predicate[0]));
        
        // Apply sorting
        if (pageable.getSort().isSorted()) {
            List<Order> orders = pageable.getSort().stream()
                    .map(order -> order.isAscending() 
                            ? cb.asc(root.get(order.getProperty()))
                            : cb.desc(root.get(order.getProperty())))
                    .collect(Collectors.toList());
            query.orderBy(orders);
        }
        
        TypedQuery<User> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<User> results = typedQuery.getResultList();
        long total = countTotal(filter);
        
        return new PageImpl<>(results, pageable, total);
    }
    
    private List<Predicate> buildPredicates(UserFilter filter, CriteriaBuilder cb, Root<User> root) {
        List<Predicate> predicates = new ArrayList<>();
        
        Optional.ofNullable(filter.getName())
                .filter(StringUtils::hasText)
                .ifPresent(name -> {
                    Predicate firstNameLike = cb.like(cb.lower(root.get("firstName")), "%" + name.toLowerCase() + "%");
                    Predicate lastNameLike = cb.like(cb.lower(root.get("lastName")), "%" + name.toLowerCase() + "%");
                    predicates.add(cb.or(firstNameLike, lastNameLike));
                });
        
        Optional.ofNullable(filter.getStatus())
                .ifPresent(status -> predicates.add(cb.equal(root.get("status"), status)));
        
        Optional.ofNullable(filter.getCreatedAfter())
                .ifPresent(date -> predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), date)));
        
        return predicates;
    }
}

// Extended repository interface
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    // Standard query methods
}
```

## Query Methods

### Query Method Naming Conventions

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Basic finders
    List<Product> findByName(String name);
    List<Product> findByNameIgnoreCase(String name);
    List<Product> findByNameContaining(String name);
    List<Product> findByNameContainingIgnoreCase(String name);
    
    // Comparison operators
    List<Product> findByPriceGreaterThan(BigDecimal price);
    List<Product> findByPriceGreaterThanEqual(BigDecimal price);
    List<Product> findByPriceLessThan(BigDecimal price);
    List<Product> findByPriceBetween(BigDecimal startPrice, BigDecimal endPrice);
    
    // Collection operations
    List<Product> findByCategoryIn(Collection<String> categories);
    List<Product> findByCategoryNotIn(Collection<String> categories);
    
    // Null checks
    List<Product> findByDescriptionIsNull();
    List<Product> findByDescriptionIsNotNull();
    
    // Boolean conditions
    List<Product> findByActiveTrue();
    List<Product> findByActiveFalse();
    
    // Date operations
    List<Product> findByCreatedAtAfter(LocalDateTime date);
    List<Product> findByCreatedAtBefore(LocalDateTime date);
    List<Product> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Complex combinations
    List<Product> findByNameContainingAndPriceGreaterThanAndActiveTrue(
            String name, BigDecimal price);
    
    // Ordering
    List<Product> findByActiveTrueOrderByNameAsc();
    List<Product> findByActiveTrueOrderByPriceDescNameAsc();
    
    // Limiting results
    Product findFirstByActiveTrueOrderByCreatedAtDesc();
    List<Product> findTop10ByActiveTrueOrderByPriceAsc();
    
    // Distinct results
    List<String> findDistinctCategoryByActiveTrue();
}
```

### Custom JPQL Queries

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT o FROM Order o WHERE o.customer.email = :email AND o.status = :status")
    List<Order> findByCustomerEmailAndStatus(@Param("email") String email, 
                                           @Param("status") OrderStatus status);
    
    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);
    
    @Query("SELECT o FROM Order o " +
           "JOIN FETCH o.customer " +
           "JOIN FETCH o.orderItems oi " +
           "JOIN FETCH oi.product " +
           "WHERE o.customer.id = :customerId")
    List<Order> findByCustomerIdWithAllAssociations(@Param("customerId") Long customerId);
    
    @Query("SELECT new com.company.dto.OrderSummary(o.id, o.orderDate, o.totalAmount, c.email) " +
           "FROM Order o JOIN o.customer c " +
           "WHERE o.orderDate BETWEEN :startDate AND :endDate")
    List<OrderSummary> findOrderSummariesBetweenDates(@Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.orderDate >= :date")
    BigDecimal getTotalRevenueFromDate(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId AND o.status = 'COMPLETED'")
    long countCompletedOrdersByCustomer(@Param("customerId") Long customerId);
    
    // Modifying queries
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus WHERE o.status = :oldStatus AND o.orderDate < :date")
    int updateOrderStatusByDateAndCurrentStatus(@Param("newStatus") OrderStatus newStatus,
                                              @Param("oldStatus") OrderStatus oldStatus,
                                              @Param("date") LocalDateTime date);
    
    @Modifying
    @Query("DELETE FROM Order o WHERE o.status = 'CANCELLED' AND o.orderDate < :date")
    int deleteCancelledOrdersOlderThan(@Param("date") LocalDateTime date);
}
```

### Native SQL Queries

```java
@Repository
public interface ReportRepository extends JpaRepository<Order, Long> {
    
    @Query(value = """
            SELECT 
                DATE_TRUNC('day', o.order_date) as order_date,
                COUNT(*) as order_count,
                SUM(o.total_amount) as total_revenue
            FROM orders o 
            WHERE o.order_date >= :startDate 
            GROUP BY DATE_TRUNC('day', o.order_date)
            ORDER BY order_date
            """, nativeQuery = true)
    List<Object[]> getDailySalesReport(@Param("startDate") LocalDateTime startDate);
    
    @Query(value = """
            SELECT p.name, SUM(oi.quantity) as total_sold
            FROM order_items oi
            JOIN products p ON oi.product_id = p.id
            JOIN orders o ON oi.order_id = o.id
            WHERE o.order_date >= :startDate
            GROUP BY p.id, p.name
            ORDER BY total_sold DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> getTopSellingProducts(@Param("startDate") LocalDateTime startDate,
                                        @Param("limit") int limit);
    
    @Query(value = """
            WITH monthly_revenue AS (
                SELECT 
                    DATE_TRUNC('month', order_date) as month,
                    SUM(total_amount) as revenue
                FROM orders 
                WHERE order_date >= :startDate
                GROUP BY DATE_TRUNC('month', order_date)
            )
            SELECT 
                month,
                revenue,
                LAG(revenue) OVER (ORDER BY month) as previous_month_revenue,
                ROUND(((revenue - LAG(revenue) OVER (ORDER BY month)) / 
                       LAG(revenue) OVER (ORDER BY month) * 100), 2) as growth_percentage
            FROM monthly_revenue
            ORDER BY month
            """, nativeQuery = true)
    List<Object[]> getMonthlyGrowthReport(@Param("startDate") LocalDateTime startDate);
}
```

## Entity Design and Relationships

### Entity Best Practices

```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_last_name", columnList = "last_name"),
    @Index(name = "idx_user_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    @Column(nullable = false, unique = true, length = 100)
    private String email;
    
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Relationships
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Order> orders = new ArrayList<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @ToString.Exclude
    private Set<Role> roles = new HashSet<>();
    
    // Helper methods
    public void addRole(Role role) {
        roles.add(role);
        role.getUsers().add(this);
    }
    
    public void removeRole(Role role) {
        roles.remove(role);
        role.getUsers().remove(this);
    }
}
```

### Relationship Mapping Patterns

```java
// One-to-Many with proper cascade and fetch
@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    // Bidirectional One-to-Many
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, 
               fetch = FetchType.LAZY, orphanRemoval = true)
    @ToString.Exclude
    private List<Order> orders = new ArrayList<>();
    
    public void addOrder(Order order) {
        orders.add(order);
        order.setCustomer(this);
    }
    
    public void removeOrder(Order order) {
        orders.remove(order);
        order.setCustomer(null);
    }
}

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;
    
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    // Many-to-One with proper fetch
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private Customer customer;
    
    // One-to-Many with composition
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, 
               fetch = FetchType.LAZY, orphanRemoval = true)
    @ToString.Exclude
    private List<OrderItem> orderItems = new ArrayList<>();
    
    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }
    
    public void removeOrderItem(OrderItem orderItem) {
        orderItems.remove(orderItem);
        orderItem.setOrder(null);
    }
}

// Value Object Pattern
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    
    @Column(name = "street_address")
    private String streetAddress;
    
    @Column(name = "city")
    private String city;
    
    @Column(name = "state")
    private String state;
    
    @Column(name = "postal_code")
    private String postalCode;
    
    @Column(name = "country")
    private String country;
}

// Using Embedded objects
@Entity
@Table(name = "customers")
@Data
public class Customer {
    // ... other fields
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "streetAddress", column = @Column(name = "billing_street")),
        @AttributeOverride(name = "city", column = @Column(name = "billing_city")),
        @AttributeOverride(name = "state", column = @Column(name = "billing_state")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "billing_postal_code")),
        @AttributeOverride(name = "country", column = @Column(name = "billing_country"))
    })
    private Address billingAddress;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "streetAddress", column = @Column(name = "shipping_street")),
        @AttributeOverride(name = "city", column = @Column(name = "shipping_city")),
        @AttributeOverride(name = "state", column = @Column(name = "shipping_state")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "shipping_postal_code")),
        @AttributeOverride(name = "country", column = @Column(name = "shipping_country"))
    })
    private Address shippingAddress;
}
```

## Transaction Management

### Service Layer Transaction Management

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final CustomerService customerService;
    private final EmailService emailService;
    
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.getCustomerId());
        
        Customer customer = customerService.getCustomerById(request.getCustomerId());
        
        Order order = Order.builder()
                .customer(customer)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productService.getProductById(itemRequest.getProductId());
            
            // Check inventory
            if (product.getStock() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(
                    "Not enough stock for product: " + product.getName());
            }
            
            // Reduce inventory
            product.setStock(product.getStock() - itemRequest.getQuantity());
            productService.updateProduct(product);
            
            // Add order item
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            
            order.addOrderItem(orderItem);
            totalAmount = totalAmount.add(
                product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
            );
        }
        
        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);
        
        // Send confirmation email (should not fail the transaction)
        try {
            emailService.sendOrderConfirmation(savedOrder);
        } catch (Exception e) {
            log.warn("Failed to send order confirmation email for order: {}", 
                    savedOrder.getId(), e);
        }
        
        return savedOrder;
    }
    
    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        
        // Business logic based on status change
        if (newStatus == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED) {
            restoreInventory(order);
        }
        
        return orderRepository.save(order);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPayment(Long orderId, PaymentDetails paymentDetails) {
        // This runs in a separate transaction
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        
        // Process payment logic here
        // If payment fails, this transaction will rollback
        // but won't affect the calling transaction
    }
    
    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId, pageable);
    }
    
    private void restoreInventory(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
            productService.updateProduct(product);
        }
    }
}
```

### Transaction Configuration

```java
@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }
    
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
```

### Programmatic Transaction Management

```java
@Service
@RequiredArgsConstructor
public class BulkOperationService {
    
    private final TransactionTemplate transactionTemplate;
    private final UserRepository userRepository;
    
    public BulkUpdateResult bulkUpdateUsers(List<UpdateUserRequest> updates) {
        return transactionTemplate.execute(status -> {
            int successCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (UpdateUserRequest update : updates) {
                try {
                    User user = userRepository.findById(update.getUserId())
                            .orElseThrow(() -> new EntityNotFoundException("User", update.getUserId()));
                    
                    user.setFirstName(update.getFirstName());
                    user.setLastName(update.getLastName());
                    userRepository.save(user);
                    successCount++;
                    
                } catch (Exception e) {
                    errors.add("Failed to update user " + update.getUserId() + ": " + e.getMessage());
                }
            }
            
            if (errors.size() > updates.size() / 2) {
                // If more than half failed, rollback the transaction
                status.setRollbackOnly();
                throw new BulkOperationException("Too many failures: " + errors);
            }
            
            return new BulkUpdateResult(successCount, errors);
        });
    }
}
```

## Caching Strategies

### Entity-Level Caching

```java
@Entity
@Table(name = "products")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    // Collection caching
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @ToString.Exclude
    private List<Review> reviews = new ArrayList<>();
}
```

### Query Result Caching

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.active = true")
    List<Product> findByCategoryAndActiveTrue(@Param("category") String category);
    
    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheRegion", value = "product-search")
    })
    @Query("SELECT p FROM Product p WHERE p.name LIKE %:name% ORDER BY p.name")
    List<Product> searchByName(@Param("name") String name);
}
```

### Service-Level Caching

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    
    private final ProductRepository productRepository;
    
    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) {
        log.info("Fetching product from database: {}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product", id));
    }
    
    @Cacheable(value = "product-lists", key = "#category")
    public List<Product> getProductsByCategory(String category) {
        log.info("Fetching products by category from database: {}", category);
        return productRepository.findByCategoryAndActiveTrue(category);
    }
    
    @CachePut(value = "products", key = "#result.id")
    public Product updateProduct(Product product) {
        log.info("Updating product: {}", product.getId());
        return productRepository.save(product);
    }
    
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        log.info("Deleting product: {}", id);
        productRepository.deleteById(id);
    }
    
    @CacheEvict(value = {"products", "product-lists"}, allEntries = true)
    public void clearProductCaches() {
        log.info("Clearing all product caches");
    }
    
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#product.id"),
        @CacheEvict(value = "product-lists", key = "#product.category")
    })
    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }
}
```

### Cache Configuration

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
    
    @Bean
    public Cache productsCache() {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()
                .build();
    }
}
```

## Database Migration Patterns

### Flyway Integration

```java
@Configuration
public class FlywayConfig {
    
    @Bean
    @Primary
    public Flyway flyway(@Value("${spring.datasource.url}") String url,
                        @Value("${spring.datasource.username}") String username,
                        @Value("${spring.datasource.password}") String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}
```

### Migration Scripts

```sql
-- V1__Create_users_table.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_last_name ON users(last_name);
CREATE INDEX idx_users_created_at ON users(created_at);

-- V2__Create_roles_table.sql
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

INSERT INTO roles (name, description) VALUES 
('USER', 'Standard user role'),
('ADMIN', 'Administrator role');

-- V3__Add_user_profile_fields.sql
ALTER TABLE users 
ADD COLUMN phone VARCHAR(20),
ADD COLUMN date_of_birth DATE,
ADD COLUMN profile_image_url VARCHAR(500);

-- V4__Create_orders_table.sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    order_date TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (customer_id) REFERENCES users(id)
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_order_date ON orders(order_date);
CREATE INDEX idx_orders_status ON orders(status);
```

## Best Practices Summary

### Repository Design
- Use specific return types (Optional, List, Page)
- Implement custom repositories for complex queries
- Use query method naming conventions consistently
- Leverage @Query for complex JPQL/SQL queries

### Entity Design
- Use proper fetch strategies (LAZY by default)
- Implement equals/hashCode correctly
- Use @CreationTimestamp and @UpdateTimestamp
- Add appropriate indexes
- Use @Version for optimistic locking

### Transaction Management
- Use @Transactional at service layer
- Set readOnly=true for read-only operations
- Handle exceptions appropriately
- Use proper propagation levels
- Keep transactions short and focused

### Performance Optimization
- Use pagination for large datasets
- Implement caching strategically
- Use batch operations for bulk updates
- Monitor query performance
- Use connection pooling
- Implement proper indexing

### Testing
- Use @DataJpaTest for repository tests
- Use TestContainers for integration tests
- Test custom queries thoroughly
- Verify transaction behavior
- Test caching behavior
