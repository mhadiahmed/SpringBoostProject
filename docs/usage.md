# Usage Guide

A complete walkthrough for using Spring Boost in a real Spring Boot project —
what to install, how to register it with your AI editor, what each mode
actually gives you, and what to check if something doesn't work.

Everything here reflects **v0.2.0**, verified end-to-end against the actual
artifact pulled fresh from Maven Central (not just a local build) — both the
stdio connection and the embedded-dependency path. See
[KNOWN_ISSUES.md](../KNOWN_ISSUES.md) for the full verification history and
root-cause writeups.

## Two ways to use Spring Boost

Pick based on what you want:

| | Standalone (stdio) | Embedded (Maven/Gradle dependency) |
|---|---|---|
| **What it is** | A separate `java -jar ... mcp` process your AI editor spawns per session | `spring-boost` added as a dependency of your own app; runs inside your app's own JVM |
| **Setup** | Download/build the jar, register the command with your editor | Add one dependency, nothing else required |
| **`application-info` / `database-schema` / etc.** | Honest error — a separate process structurally can't see your app's beans | Real data — sees your actual beans, `DataSource`, JPA metamodel |
| **Best for** | Quick setup, works with any project without touching its `pom.xml` | Deeper introspection tools, if you don't mind the dependency |

You can use either one alone, or both together (embedding doesn't replace the
stdio registration — they're independent).

## Standalone setup (recommended starting point)

1. **Get the executable jar** from the
   [v0.2.0 GitHub Release](https://github.com/mhadiahmed/SpringBoostProject/releases/tag/v0.2.0),
   or pull it straight from Maven Central:

   ```bash
   mvn dependency:get -Dartifact=io.github.mhadiahmed:spring-boost:0.2.0:jar:exec
   # jar lands in ~/.m2/repository/io/github/mhadiahmed/spring-boost/0.2.0/spring-boost-0.2.0-exec.jar
   ```

   You specifically want the `-exec` classified jar — the classifier-less
   jar is a plain library jar with no `Main-Class` and isn't runnable.

2. **From your Spring Boot project's root, publish the guidelines/skills:**

   ```bash
   java -jar /path/to/spring-boost-0.2.0-exec.jar install
   ```

   This detects your project's actual dependencies (Spring Data JPA, Spring
   Security, Thymeleaf, etc.) and only publishes the guidelines relevant to
   what you're using, into `.ai/guidelines/` and `.ai/skills/`. It also prints
   the exact registration command for your editor.

3. **Register with your editor** (also printed by `install`):

   ```bash
   # Claude Code
   claude mcp add -s local -t stdio spring-boost -- java -jar /path/to/spring-boost-0.2.0-exec.jar mcp

   # Codex
   codex mcp add spring-boost -- java -jar /path/to/spring-boost-0.2.0-exec.jar mcp

   # Gemini CLI
   gemini mcp add -s project -t stdio spring-boost java -jar /path/to/spring-boost-0.2.0-exec.jar mcp
   ```

   For Cursor or manual registration, see the JSON snippet in the
   [README](../README.md#-ai-client-setup).

4. **Verify it connected.** For Claude Code: `claude mcp get spring-boost`
   should show `✔ Connected`. The very first check after a fresh machine (or
   after the background daemon hasn't run in a while) takes a few seconds
   while it warms up — that's expected, not a hang. If it still says
   `✘ Failed to connect` after that, see [Troubleshooting](#troubleshooting).

## Embedded setup (real app introspection)

Add the dependency — nothing else is required, it auto-configures itself:

```xml
<dependency>
    <groupId>io.github.mhadiahmed</groupId>
    <artifactId>spring-boost</artifactId>
    <version>0.2.0</version>
</dependency>
```

```gradle
implementation 'io.github.mhadiahmed:spring-boost:0.2.0'
```

When your app starts, this registers the MCP tool registry and a `/mcp`
WebSocket endpoint inside your app's own Spring context (sharing your app's
Tomcat/port, not a separate server) — so tools like `application-info` return
your app's real `spring.application.name`, port, and working directory, not
spring-boost's own. Confirmed this way against a real Spring Boot project this
pass, byte-for-byte over a real WebSocket handshake.

Disable it without removing the dependency:

```yaml
spring-boost:
  mcp:
    enabled: false
```

It only activates for web applications (`@ConditionalOnWebApplication`) — a
batch job or CLI-only Spring Boot app with this dependency won't trigger it.

## What's working now

Verified this pass, not just claimed:

- **stdio MCP connection** — real root cause found and fixed (a JSON-RPC
  envelope bug, not JVM speed as previously assumed). `claude mcp get` shows
  `Connected` reliably, confirmed against the actual Central-downloaded jar.
- **Shared warm daemon** — repeat sessions connect in well under a second;
  only the first one pays JVM+Spring boot cost. Safe across projects on
  different spring-boost versions (each gets its own daemon, keyed by jar
  identity — no cross-version collisions).
- **9 core MCP tools**, exact parity with Laravel Boost's tool set (see the
  [README's tool table](../README.md) for the full list), plus 5 opt-in
  Spring-specific extensions.
- **`install`/`update`** — package-aware: only publishes guidelines/skills
  relevant to dependencies your project actually has, not the entire bundle.
- **`search-docs`** — indexes 151 real chunks from the bundled guideline
  corpus (lazily, so it doesn't cost startup time unless actually used) — not
  a hosted 17k-document API like Laravel Boost's (there's no Spring
  equivalent of that infrastructure yet; see
  [Recommendations](#recommended-next-steps)). Note: `embeddings-provider:
  openai` and `local` are both unimplemented stubs today — they log a
  warning and silently fall back to `simple` (a lightweight, deterministic,
  non-ML embedding). `simple` is the only real option currently; don't rely
  on the config implying real OpenAI/local-model semantic search exists yet.
- **Embedded/`IN_PROCESS` introspection** — `application-info` and friends
  see your real app when embedded, verified against a real Spring Boot
  project.
- **Honest standalone-mode errors** — in stdio mode, tools that can't see
  your app (`application-info`, `database-schema`, etc.) say so explicitly
  with an actionable hint, instead of silently reporting spring-boost's own
  state.

## Configuration reference

```yaml
spring-boost:
  mcp:
    enabled: true                      # master switch (also gates auto-configuration when embedded)
    tools:
      database-access: true
      code-execution: false            # enable with caution — arbitrary SpEL execution
      extensions-enabled: false        # opt in to the 5 Spring-only tools beyond Boost parity
  documentation:
    enabled: true
    embeddings-provider: simple        # the only real implementation right now — see note below
    cache-size: 1000
  security:
    sandbox:
      enabled: true
      allowed-packages: ["com.example"]
```

## Troubleshooting

**`claude mcp get` (or equivalent) shows "Failed to connect."**
- Check `~/.spring-boost/daemon-*.log` for the relevant daemon's boot log.
- Make sure the registered command's jar path is correct and absolute (a
  relative path resolves against whatever working directory your editor
  spawns the process from, which may not be your project root).
- If it's been stuck for a while, kill the daemon and let it restart fresh:
  `pkill -f mcp-daemon`, then retry the connection (it auto-restarts on the
  next attempt).

**Where do daemon files live?**
`~/.spring-boost/daemon-<key>.{port,lock,log}` — one triple per distinct jar
(so different spring-boost versions never collide). Safe to delete the whole
`~/.spring-boost/` directory while no daemon is running; it gets recreated on
next use.

**First connection is slow, every one after is fast — is that a bug?**
No — that's the daemon warming up (JVM + Spring context boot), paid once.
See the [README's connection reliability note](../README.md#-ai-client-setup).

**Embedded mode doesn't seem to activate.**
Check you're running a real servlet web application (not `WebApplicationType.NONE`)
and that `spring-boost.mcp.enabled` isn't set to `false`.

## Recommended next steps

Honest assessment of what's still worth doing, roughly in priority order:

1. **CLI boot-noise cleanup** (tracked as issue #5 in
   [KNOWN_ISSUES.md](../KNOWN_ISSUES.md)) — `install`/`update` still print
   ~27 lines of Spring/Hibernate boot chatter before the real output.
2. **Shrink the jar.** The `-exec` jar is ~107MB — it bundles Postgres/MySQL/H2
   drivers, Testcontainers, an OpenAI client, flexmark, jsoup, and WebFlux
   whether or not a given user needs all of them. Sonatype already flagged
   this project as approaching its monthly release-size publishing limit.
   Worth auditing which dependencies are load-bearing vs. leftover.
3. **Daemon lifecycle commands.** There's no user-facing way to check daemon
   status or stop it cleanly today beyond `pkill`/deleting `~/.spring-boost/`.
   A `spring-boost daemon status`/`stop` subcommand would help.
4. **Windows verification.** The daemon/launcher architecture has an
   `isWindows()` branch for the `/dev/null` equivalent, but has only ever
   actually run on macOS this whole project. Untested on Windows entirely.
5. **CI is now fixed but unproven.** `ci.yml` was rewritten this pass (the
   previous version referenced `./mvnw`, which was never committed, and
   failed instantly on every single run in this project's history — meaning
   no test suite run, including the new regression tests, was ever actually
   verified by CI). Watch the next few pushes to confirm it's green for real.
6. **Real documentation search.** `search-docs` is a local, guideline-derived
   corpus (151 chunks) — a reasonable honest substitute, but nowhere near
   Laravel Boost's hosted 17,000+ document API. If this project wants to
   close that gap meaningfully, it needs either a maintained hosted corpus or
   a much larger bundled one. Related: `embeddings-provider: openai`/`local`
   are unimplemented stubs that silently fall back to `simple` — either wire
   up a real implementation or remove the config options so they stop
   implying a choice that doesn't exist.
7. **`docker-compose.yml` hasn't been independently re-verified** this pass
   (references a Redis service that nothing in the codebase actually uses —
   likely template leftover). Worth auditing alongside the jar-size cleanup.
