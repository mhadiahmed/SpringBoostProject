---
name: spring-security-development
package: Spring Security
---

# Spring Security Development

Load when adding authentication, authorization, method security, or touching
`SecurityFilterChain` config. Broad conventions live in
`.ai/guidelines/core/spring-security.md`.

## Checklist

- One `SecurityFilterChain` `@Bean` per concern (e.g. `apiFilterChain`,
  `formLoginFilterChain`) ordered with `@Order`, not one giant chain with
  branching `if`s.
- Prefer method security (`@PreAuthorize("hasRole('ADMIN')")`) over manual
  `SecurityContextHolder` checks inside business logic.
- Passwords: `PasswordEncoder` bean is always `BCryptPasswordEncoder` (or
  `Argon2` for new projects) — never roll your own hashing.
- CSRF stays enabled for anything serving browser sessions; disable only for
  stateless token APIs (`SessionCreationPolicy.STATELESS`).
- CORS: configure via `CorsConfigurationSource` bean, not `@CrossOrigin` on
  every controller.

## JWT resource server (stateless API)

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    http.csrf(CsrfConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

## Testing

Use `@WithMockUser(roles = "ADMIN")` for controller/service security tests
instead of standing up real authentication.
