package com.springboost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;
import java.util.Set;

/**
 * Spring Boost Application - MCP Server for AI-assisted Spring Boot development
 *
 * This application provides a Model Context Protocol (MCP) server that enables
 * AI coding assistants to interact with Spring Boot applications through
 * specialized tools and context-aware documentation.
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
@ComponentScan(basePackages = "com.springboost")
public class SpringBoostApplication {

    /** CLI subcommands that must never boot a web server or print to stdout themselves. */
    private static final Set<String> HEADLESS_COMMANDS = Set.of("mcp-daemon", "install", "update");

    /**
     * Tracks how spring-boost was launched. Tools that need access to the
     * target application's beans (DataSource, JPA metamodel, etc.) can only
     * work when running {@code IN_PROCESS}; in {@code STANDALONE} mode
     * (the {@code java -jar spring-boost.jar mcp} stdio path) those tools
     * return an honest error instead of silently reporting spring-boost's
     * own internal state.
     */
    public enum Mode { STANDALONE, IN_PROCESS }

    private static volatile Mode currentMode = Mode.STANDALONE;

    public static Mode getCurrentMode() { return currentMode; }

    /**
     * Called by {@link com.springboost.autoconfigure.SpringBoostAutoConfiguration}
     * when spring-boost is pulled in as a dependency of a host Spring Boot app
     * (rather than run via this class's own {@code main()}), so introspection
     * tools know they're sharing the host's beans/DataSource/JPA metamodel.
     */
    public static void activateInProcessMode() {
        currentMode = Mode.IN_PROCESS;
    }

    /**
     * Reads the real build version from the jar manifest's
     * Implementation-Version (set automatically by the Maven/Gradle build
     * from the project version), so reported version numbers can't drift
     * from the actual released artifact the way three separate hardcoded
     * "0.1.0" literals once did across an entire release.
     */
    public static String getVersion() {
        String version = SpringBoostApplication.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "mcp".equals(args[0])) {
            // Fast path: skip Spring entirely. Delegates straight to the
            // Spring-free thin launcher, which connects to (auto-starting if
            // needed) a shared daemon that keeps a Spring context warm --
            // instead of paying ~5s of context boot on every stdio session,
            // which was blowing past Claude Code's connection timeout.
            com.springboost.launcher.ThinLauncher.main(args);
            return;
        }

        System.setProperty("spring.application.name", "spring-boost");
        System.setProperty("spring.banner.location", "classpath:banner.txt");

        String subcommand = args.length > 0 ? args[0] : null;
        boolean headless = subcommand != null && HEADLESS_COMMANDS.contains(subcommand);
        boolean quiet = Arrays.asList(args).contains("--quiet") || Arrays.asList(args).contains("-q");

        if (!headless) {
            // Long-running server (WebSocket mode) — tools CAN see real app state
            // when the consumer's app embeds spring-boost as a dependency.
            currentMode = Mode.IN_PROCESS;
        } else {
            currentMode = Mode.STANDALONE;
        }

        SpringApplication app = new SpringApplication(SpringBoostApplication.class);
        app.setAdditionalProfiles("mcp-server");

        if (headless) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(Banner.Mode.OFF);
            app.setLogStartupInfo(false);

            // --- Startup performance: exclude auto-configurations irrelevant to stdio MCP ---
            // WebSocket/HTTP server infrastructure (NONE web type already skips Tomcat,
            // but the auto-config still runs expensive bean registration).
            System.setProperty("spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration");

            // Suppress noisy Hibernate DDL + HikariCP INFO logs that dominate stderr.
            System.setProperty("logging.level.org.hibernate.SQL", "ERROR");
            System.setProperty("logging.level.org.hibernate.type.descriptor.sql.BasicBinder", "ERROR");
            System.setProperty("logging.level.com.zaxxer.hikari", "ERROR");
            System.setProperty("logging.level.org.hibernate", "ERROR");
        }

        if (quiet || headless) {
            // For CLI subcommands (install, update, --list-tools, --validate-config,
            // and mcp), suppress noisy boot logs so the user sees only the actual
            // command output. The mcp subcommand already redirects stdout; this
            // additionally quiets the INFO/DEBUG chatter on stderr.
            System.setProperty("logging.level.org.hibernate", "WARN");
            System.setProperty("logging.level.com.zaxxer.hikari", "WARN");
            System.setProperty("logging.level.org.springframework.boot.autoconfigure", "WARN");
            System.setProperty("logging.level.org.springframework.web", "WARN");
            System.setProperty("logging.level.com.springboost", quiet ? "WARN" : "ERROR");
        }

        long startNanos = System.nanoTime();

        try {
            var context = app.run(args);
            long bootMs = (System.nanoTime() - startNanos) / 1_000_000;

            // Any CLI subcommand/flag (args.length > 0) has already closed the
            // context by now via SpringApplication.exit() in BoostCommand; only
            // the true long-running server (no args) reaches this log safely.
            if (args.length == 0) {
                log.info("Spring Boost MCP Server started successfully in {}ms!", bootMs);
                log.info("Available tools: {}", context.getBeansOfType(com.springboost.mcp.tools.McpTool.class).size());
            } else if ("mcp-daemon".equals(subcommand)) {
                log.info("MCP daemon context ready in {}ms", bootMs);
            }
        } catch (Exception e) {
            log.error("Failed to start Spring Boost MCP Server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
