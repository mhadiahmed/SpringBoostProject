---
name: spring-webflux-development
package: Spring WebFlux
---

# Spring WebFlux Development

Load when building reactive endpoints, `WebClient` calls, or `Mono`/`Flux`
pipelines. Reactive and blocking (Spring MVC) code should never mix in the
same request path.

## Checklist

- Never call a blocking API (JDBC, `RestTemplate`, blocking I/O) inside a
  reactive chain — it stalls the event loop for every request in flight. Wrap
  with `Schedulers.boundedElastic()` only as a last resort; prefer a reactive
  driver (R2DBC, `WebClient`).
- Return `Mono<T>` for single results, `Flux<T>` for streams — never
  `.block()` in production request-handling code (tests are fine).
- Compose with operators (`flatMap`, `zip`, `switchIfEmpty`) instead of nested
  `subscribe()` calls.
- Backpressure: use `Flux.buffer()`/`onBackpressureDrop()` deliberately, don't
  let an unbounded producer overwhelm a slow consumer.

## WebClient call

```java
Mono<UserDto> user = webClient.get()
    .uri("/users/{id}", id)
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError, r -> Mono.error(new NotFoundException()))
    .bodyToMono(UserDto.class)
    .timeout(Duration.ofSeconds(3));
```

## Testing

`WebTestClient` for endpoint tests, `StepVerifier` for asserting on a
`Mono`/`Flux` directly — not `.block()` followed by a plain assertion.
