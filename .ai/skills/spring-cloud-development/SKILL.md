---
name: spring-cloud-development
package: Spring Cloud
---

# Spring Cloud Development

Load when working across service boundaries: service discovery, config
server, gateway routing, or resilience (circuit breakers/retries).

## Checklist

- Service-to-service calls go through a discovery-aware client
  (`WebClient`/`RestClient` with `@LoadBalanced`, or the Feign client) —
  never hardcode a peer's host:port.
- Wrap cross-service calls likely to fail transiently in a Resilience4j
  circuit breaker + retry, with a `@Recover`/fallback that degrades
  gracefully instead of propagating the failure.
- Externalize environment-specific config to Spring Cloud Config or a
  `ConfigMap`/env vars — don't branch on `spring.profiles.active` inside
  business logic to pick config values.
- Gateway routes belong in `application.yml` (`spring.cloud.gateway.routes`)
  or a `RouteLocator` bean, not in ad-hoc controllers acting as a proxy.

## Circuit breaker

```java
@CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackStock")
public StockLevel getStock(String sku) {
    return inventoryClient.getStock(sku);
}

private StockLevel fallbackStock(String sku, Throwable t) {
    return StockLevel.unknown(sku);
}
```

## Testing

Use WireMock (or a Testcontainers-hosted stub) for downstream services in
integration tests — don't hit real peer services from the test suite.
