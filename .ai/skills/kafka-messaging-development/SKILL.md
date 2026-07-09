---
name: kafka-messaging-development
package: Spring Kafka
---

# Kafka Messaging Development

Load when producing/consuming Kafka messages. Broader messaging conventions
live in `.ai/guidelines/ecosystem/messaging/spring-messaging.md`.

## Checklist

- Key messages by the entity they affect (e.g. `orderId`) so related events
  land on the same partition and stay ordered relative to each other.
- Consumers must be idempotent — Kafka's at-least-once delivery means the
  same message can arrive twice; dedupe on a business key or use an
  idempotent write (upsert) rather than assuming exactly-once.
- Configure a dead-letter topic (`DefaultErrorHandler` +
  `DeadLetterPublishingRecoverer`) instead of letting a poison message block
  the consumer group forever.
- Serialize with a schema (Avro/Protobuf + Schema Registry, or at minimum a
  versioned JSON DTO) — don't pass raw `Map<String,Object>` across a service
  boundary.

## Producer / consumer

```java
@Service
@RequiredArgsConstructor
class OrderEventPublisher {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    void publish(OrderEvent event) {
        kafkaTemplate.send("order-events", event.orderId(), event);
    }
}

@KafkaListener(topics = "order-events", groupId = "inventory-service")
void onOrderEvent(OrderEvent event) {
    inventoryService.reserveStock(event); // must be idempotent
}
```

## Testing

`@EmbeddedKafka` for fast unit-style tests of listener wiring; Testcontainers'
Kafka module when the test needs real broker semantics (partitioning,
consumer group rebalancing).
