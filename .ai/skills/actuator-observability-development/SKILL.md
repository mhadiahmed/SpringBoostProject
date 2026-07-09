---
name: actuator-observability-development
package: Spring Boot Actuator / Micrometer
---

# Actuator & Observability Development

Load when adding health checks, metrics, or tracing — i.e. making a feature
observable in production. Broader conventions live in
`.ai/guidelines/ecosystem/monitoring/spring-observability.md`.

## Checklist

- New external dependency (DB, queue, downstream API) gets a custom
  `HealthIndicator` if its failure should flip `/actuator/health` to DOWN —
  don't rely solely on the generic datasource health check for
  application-specific dependencies.
- Business metrics use Micrometer (`Counter`, `Timer`, `Gauge`) via
  `MeterRegistry`, tagged consistently (e.g. `outcome=success|failure`) so
  they're sliceable in Prometheus/Grafana without a code change.
- Only expose the actuator endpoints actually needed
  (`management.endpoints.web.exposure.include`) — `include: "*"` in
  production is a common finding in security reviews, not a default to ship.
- Trace external calls (`@Observed` or manual `Observation` spans) when
  latency of a specific downstream call matters for debugging, not every
  method indiscriminately.

## Custom health indicator + metric

```java
@Component
class PaymentGatewayHealthIndicator implements HealthIndicator {
    private final PaymentGatewayClient client;

    public Health health() {
        return client.ping() ? Health.up().build() : Health.down().build();
    }
}

@Service
@RequiredArgsConstructor
class OrderService {
    private final MeterRegistry meterRegistry;

    void placeOrder(Order order) {
        meterRegistry.counter("orders.placed", "channel", order.channel()).increment();
    }
}
```
