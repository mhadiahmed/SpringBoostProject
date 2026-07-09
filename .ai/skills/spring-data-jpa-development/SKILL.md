---
name: spring-data-jpa-development
package: Spring Data JPA
---

# Spring Data JPA Development

Load this skill when writing or modifying repositories, entities, or JPQL/native
queries. For broad conventions see `.ai/guidelines/core/spring-data.md` — this
skill is the on-demand cheat sheet for the task in front of you, not the full
reference.

## Checklist

- Prefer query-method derivation (`findByEmailAndActiveTrue`) over `@Query` until
  the method name gets unreadable (~4 conditions), then switch to JPQL.
- Default fetch type is `LAZY` for every `@OneToMany`/`@ManyToOne`. Never make a
  collection `EAGER` to fix an `N+1` — use `JOIN FETCH` or `@EntityGraph` instead.
- Every entity needs `@Version` for optimistic locking if it's ever updated
  concurrently (orders, inventory counts, anything with a status field).
- `@Transactional(readOnly = true)` at the class level of a service, override
  with plain `@Transactional` only on the methods that write.
- Paginate anything that can return more than ~100 rows: `Page<T>` +
  `Pageable`, never `List<T>` for unbounded queries.

## N+1 detection

```java
@Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.customer.id = :id")
List<Order> findByCustomerIdWithItems(@Param("id") Long id);
```

If `show-sql: true` reveals a query-per-row loop, that's the tell — fix with
`JOIN FETCH` (single collection) or `@EntityGraph` (multiple collections).

## Migrations

New column/table → new Flyway script `V{next}__description.sql` in
`src/main/resources/db/migration`, never edit an already-applied migration.
