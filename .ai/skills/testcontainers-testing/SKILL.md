---
name: testcontainers-testing
package: Testcontainers
---

# Testcontainers Testing

Load when writing integration tests that need a real database, broker, or
other external service instead of H2/mocks.

## Checklist

- One `@Container` static instance shared across the test class (or a shared
  `@ServiceConnection` base class) — don't start a fresh container per test
  method, it's slow and usually unnecessary.
- Use `@ServiceConnection` (Spring Boot 3.1+) to wire the container's
  connection details automatically instead of hand-written
  `@DynamicPropertySource` blocks.
- Reach for Testcontainers specifically when the thing under test depends on
  real engine behavior (Postgres-specific SQL, real Kafka ordering) — H2 or a
  mock is still correct and faster for everything else.

## Postgres example

```java
@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    OrderRepository orderRepository;

    @Test
    void savesAndReloadsOrder() {
        var saved = orderRepository.save(anOrder());
        assertThat(orderRepository.findById(saved.getId())).isPresent();
    }
}
```

## Reuse across classes

Put the `@Container` field in an abstract base class when multiple test
classes need the same container, so Testcontainers' Ryuk reaper cleans up one
container instead of one per class.
