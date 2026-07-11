# Known Issues & Required Fixes

Compiled from hands-on testing: building a real Spring Boot app (student
management, Thymeleaf CRUD) from scratch, pulling `spring-boost` fresh from
Maven Central, and registering it with Claude Code exactly as a real developer
would. Every status below is independently re-verified against actual code
and actual command output — not taken from any prior summary, including this
file's own previous versions.

## Critical — architectural, not a quick patch

### 1. MCP stdio registration fails to connect in Claude Code
**Status: fixed and verified — `claude mcp get spring-boost` now reports
`✔ Connected`.** The real root cause turned out to be different from what
every earlier pass in this file assumed.

**What we thought it was:** JVM cold-start speed. Boot time was real and was
genuinely improved (7.7s baseline → 11.9–14.4s regression from eager embedding
generation → 4.4–5.9s after making it lazy + the Spring Boot 3.5.0 bump), but
the connection kept failing at every speed, which in hindsight should have
been the signal that speed was never the actual blocker.

**What it actually was, found via a byte-level trace of a real
`claude mcp get` connection (thread dumps + temporary stdin/stdout logging on
both the launcher and daemon sides):**
1. `McpMessage`'s JSON responses hardcoded `"protocolVersion":"2024-11-05"`
   regardless of what the client asked for. Claude Code (v2.1.205) requests
   `"2025-11-25"`. A client that strictly checks the negotiated version
   against its own can reject the whole handshake over this mismatch alone.
   Fixed: `handleInitialize` now echoes back whatever `protocolVersion` the
   client sent.
2. `McpMessage.id` was typed `String`, so a numeric request id (`"id":1`) came
   back as a coerced string (`"id":"1"`) — a real JSON-RPC 2.0 violation (a
   response's id must match the request's id in both value and type). Fixed:
   `id` is now `Object`, so Jackson round-trips whatever type it deserialized.
3. **The actual blocker, confirmed by tracing actual bytes on the wire:**
   `McpMessage` has `isRequest()`/`isResponse()`/`isNotification()`/`isError()`
   convenience methods. Jackson's default bean-property introspection treats
   any `isXxx()` method as a serializable field, so *every* message — request
   or response — carried three extra, spec-illegal top-level booleans:
   `"request":false,"response":true,"notification":false`. A trace of a real
   `claude mcp get` attempt showed the daemon correctly receiving the request
   and writing a well-formed reply, and the launcher correctly relaying those
   exact bytes to Claude Code's stdin — yet Claude Code still reported
   `Failed to connect` after its full 30s timeout, every single time,
   regardless of speed. Once these methods were annotated `@JsonIgnore`, the
   identical architecture connected on the very next attempt. This is almost
   certainly what caused every failed connection in this project's entire
   history, including before this session started.

**Also built, as a genuine complementary fix (not the actual blocker, but a
real improvement kept because it works and lowers latency substantially):** a
shared daemon + thin launcher architecture. `mcp` now short-circuits in
`SpringBoostApplication.main()` before any Spring class loads, connecting to
(auto-starting if needed) one long-lived warm daemon process
(`com.springboost.launcher.ThinLauncher` ↔ `McpDaemonSubcommand` over a local
TCP socket, coordinated via a lock file + port file in `~/.spring-boost/`).
Verified: first call ~6–7.5s (one-time daemon boot), every call after that
~0.4–0.7s, concurrent sessions correctly isolated (no cross-talk). Covered by
running the actual built jar end-to-end multiple ways (direct stdio piping,
Python and Node.js subprocess clients simulating a real MCP handshake, and
finally the real `claude` CLI itself).

**End-to-end verified**, twice independently — once from inside this
session, and once by the user running `claude mcp get spring-boost` in their
own terminal after `cd`'ing into the registered project — both showing
`✔ Connected`. Covered by `McpMessageProcessorTest` (protocol version
negotiation) and `McpMessageSerializationTest` (no leaked `isXxx()` fields).

### 2. Application/Database tools report spring-boost's own state, not the target app's
**Status: solved for the embedded (`IN_PROCESS`) path, honestly mitigated for
the stdio (`STANDALONE`) path.** `java -jar spring-boost.jar mcp` is its own
JVM process with its own classpath — it structurally cannot see a separate
process's beans/`DataSource`, so `application-info`, `database-schema`, etc.
detect `STANDALONE` mode and return an honest error with an actionable hint
instead of silently reporting wrong data (`get-absolute-url` still works if
given explicit `customHost`/`customPort`).

**The embedded path (add spring-boost as a Maven/Gradle dependency) now
actually works**, verified two ways this pass:
- A real packaging bug was found and fixed first: v0.1.0's published jar was
  Spring Boot's *repackaged executable* jar (classes nested under
  `BOOT-INF/classes/`), which a normal classloader can't resolve as a library
  dependency at all — confirmed via `unzip -l`. Fixed by giving
  `spring-boot-maven-plugin`/`bootJar` a `classifier` (`exec`), so the
  classifier-less artifact is now a plain, classes-at-root library jar.
- Added real Spring Boot auto-configuration
  (`com.springboost.autoconfigure.SpringBoostAutoConfiguration` +
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
  none of which existed before). It component-scans `com.springboost.mcp`,
  `.config`, `.docs` (not `.cli`) into the host app, is gated by
  `@ConditionalOnWebApplication` + `spring-boost.mcp.enabled`, and flips
  `SpringBoostApplication.currentMode` to `IN_PROCESS`.
- Along the way, found and fixed a second real bug this surfaced:
  `WebSocketConfig` combined `setAllowedOrigins("*")` with SockJS's
  default credentialed session cookie, which is CORS-spec-illegal and threw a
  500 on every real connection attempt — nobody had hit this before because
  no host app had ever gotten this far. Fixed with `setAllowedOriginPatterns("*")`.
- **End-to-end verified** against the real student-management project: built
  a raw SockJS/WebSocket client, called `application-info` over `/mcp`, and
  got back `student-management`'s real `spring.application.name`, port, and
  working directory — not spring-boost's own. Covered by
  `SpringBoostAutoConfigurationTest` (bean registration, property-gated
  opt-out, non-web apps skipped) plus the manual end-to-end run.
- **Not yet released**: this requires v0.2.0 on Maven Central (v0.1.0 is
  immutable with the old broken jar layout). Local build/install verified
  only; publishing is a separate, later step.

### 3. `search-docs` is backed by 5 hardcoded demo documents, not real documentation
**Status: fixed, verified, and no longer costs startup time.** Was 5
hand-written paragraphs returned regardless of query relevance. Now indexes
151 chunks from the 18 bundled `.ai/guidelines/**/*.md` files — confirmed via
live `search-docs` calls returning real, topically relevant results from the
full 151-document set.

This fix originally added 4-6s to startup (indexing eagerly at boot), which
regressed issue #1. Now fixed properly: indexing is deferred until the first
real access (`DocumentationService.ensureGuidelinesLoaded()`, triggered
lazily from `getAllDocuments()`/`getDocumentById()`/etc.), so a session that
never calls `search-docs` pays nothing for it. Verified with a unit test
(`DocumentationServiceLazyLoadingTest`) and by direct measurement of restored
boot time.

## Medium — real friction, not launch-blocking

### 4. `install` publishes all guidelines/skills unconditionally
**Status: fixed, verified.** Was publishing 26 of 28 bundled files regardless
of the target project's actual dependencies (Spring Cloud, Spring Security,
Kubernetes, Docker, Kafka guidance for a project using none of them) — caused
by `isRelevant()` doing a raw substring match, and since nearly every
versioned guideline file is literally named `core.md` regardless of
framework, the substring `"core"` matched almost everything.

Fixed: relevance is now checked against the actual top-level guideline
directory (exact path-segment match, not substring), and skills are mapped to
the same category system instead of being blanket-whitelisted. Re-tested
against the student-management project (Web + Thymeleaf + Data JPA + H2 +
Validation): **14 of 28 files published** — Spring Cloud, Spring Security
5.x/6.x, Kubernetes, Docker, Kafka, and irrelevant skills all correctly
excluded; core guidelines, Spring Boot/Data version guides, database
ecosystem guidance, and relevant skills (`mcp-development`,
`spring-data-jpa-development`, `testcontainers-testing`,
`thymeleaf-development`) correctly included. Covered by
`GuidelinesPublisherTest` (2 cases: relevance filtering, `--all` override).

### 5. Every CLI invocation prints boot noise before real output
**Status: partially improved, not fully resolved** (unchanged this round —
not in scope for this pass). A `-q`/`--quiet` flag exists and suppresses some
logger output; `install` still prints ~27 lines before its actual result,
including Spring Data JPA repository-scan INFO logs and a Hibernate
deprecation WARN not covered by the current suppression list.
**Fix still needed:** extend suppression to `org.springframework.data`,
`org.hibernate.orm.deprecation`, `org.springframework.orm.jpa`.

### 6. Project's own Spring Boot baseline (3.2.0) was below start.spring.io's minimum
**Status: fixed, verified.** `start.spring.io` rejected `bootVersion=3.2.0`
outright. Bumped `spring-boot-starter-parent` (pom.xml) and the Gradle plugin
version (build.gradle) to **3.5.0** — the actual dependency baseline this
time, not just an unrelated annotation-processor pin. Rebuilt clean, full
test suite passes (23/23, including 3 new tests), and confirmed via runtime
log that the app now genuinely runs on `Spring Boot v3.5.0, Spring v6.2.7`
(Hibernate 6.6.15). This Spring Framework version bump is also the likely
reason the connection timing improved beyond just the lazy-loading fix
(newer Spring versions have generally improved startup performance).

## Fixed and verified (don't reintroduce)

Found and resolved while closing the Laravel Boost parity gap and this
follow-up pass, each verified end-to-end (built, ran, curled/measured — not
just compiled):

- **NPE on no-args startup** — `Set.of(...).contains(null)` in
  `SpringBoostApplication.main()` crashed the default long-running server mode.
- **Dockerfile broken four separate ways** — non-committed `./mvnw`;
  arm64-incompatible `eclipse-temurin:*-alpine` base; `groupadd -g 1000`
  GID collision; `.ai/` never copied into the builder stage; hardcoded
  `SPRING_PROFILES_ACTIVE=production` requiring an unbundled Postgres server.
- **Recursive fork-bomb in `ToolValidationTest`** — blindly called `execute()`
  on every tool with empty/invalid params, including `test-execution`, whose
  job is to shell out and run the project's own test suite.
- **`SearchDocsTool` NPE** — `Map.of(..., "default", null)` throws.
- **`BoostCommand` never exited on success** — CLI flags printed correct
  output then hung forever with a live Tomcat server.
- **GPG signing required `--pinentry-mode loopback`** for non-interactive use.
- **`central-publishing-maven-plugin` 0.7.0 → 0.11.0** — old version crashed
  parsing a new API response field after a successful upload.
- **`search-docs` fake 5-document corpus → 151 real guideline chunks, indexed
  lazily** (issue #3) — genuinely fixed, verified, and no longer costs
  startup time.
- **MCP stdio connection never actually worked, in this project's entire
  history** (issue #1) — root cause was `McpMessage` leaking
  `isRequest()`/`isResponse()`/`isNotification()` as spec-illegal top-level
  JSON fields via Jackson's default bean introspection, not JVM speed as
  every earlier pass assumed. Fixed alongside two other real protocol bugs
  found in the same trace (stale hardcoded `protocolVersion`, `id` type
  coercion) and a genuine daemon/thin-launcher architecture that also cuts
  warm-call latency to ~0.5s. `claude mcp get spring-boost` now reports
  `✔ Connected`, confirmed independently twice.
- **App-introspection tools silently returning wrong data → honest errors in
  `STANDALONE` mode, real introspection in `IN_PROCESS` mode** (issue #2) —
  the standalone-mode mitigation (honest errors) was already fixed; this pass
  made the embedded-dependency path actually functional: fixed the jar
  packaging bug (repackaged executable jar published where a library jar
  should be), added the missing Spring Boot auto-configuration, and fixed a
  CORS bug in `WebSocketConfig` (`setAllowedOrigins("*")` + SockJS credentialed
  cookies is spec-illegal and 500'd on every connection). End-to-end verified
  against student-management: `application-info` returns the real host app's
  identity. Not yet released to Maven Central (needs v0.2.0).
- **`install` publishing everything regardless of relevance → package-aware
  filtering** (issue #4) — genuinely fixed and verified (14/28 files for a
  targeted project, was 26/28).
- **Spring Boot baseline 3.2.0 → 3.5.0** (issue #6) — genuinely fixed and
  verified; also the likely source of additional connection-time improvement.

## Bottom line

Five of six issues are now genuinely fixed and independently re-verified
(#1, #2's embedded path, #3, #4, #6). #2's embedded-path fix isn't released to
Maven Central yet (needs v0.2.0; v0.1.0's jar is permanently broken for that
use case) — its standalone-mode path is honestly mitigated (no longer lies,
still can't see the real app, which is inherent to a separate-process stdio
transport). One is untouched this pass (#5, minor log noise).

The guideline/skill *content* was always good. Every core mechanic — stdio
connection, install, search, package-detection, and embedded introspection —
now works as designed and is verified against a real `claude mcp get`
connection and a real consuming project, not just claimed. Issue #1 in
particular is worth internalizing as a lesson: the visible symptom (slow,
failing connections) and the real cause (a protocol-envelope bug that had
nothing to do with speed) pointed in different directions for this project's
entire history, and no amount of performance tuning was ever going to fix it.
