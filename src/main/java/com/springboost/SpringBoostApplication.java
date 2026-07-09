package com.springboost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import java.io.PrintStream;
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
    private static final Set<String> HEADLESS_COMMANDS = Set.of("mcp", "install", "update");

    /**
     * The real process stdout, captured before the "mcp" subcommand redirects
     * {@link System#out} to stderr. The stdio MCP transport writes JSON-RPC
     * frames here so Spring Boot's banner/logging never corrupts the stream.
     */
    public static PrintStream REAL_STDOUT = System.out;

    public static void main(String[] args) {
        System.setProperty("spring.application.name", "spring-boost");
        System.setProperty("spring.banner.location", "classpath:banner.txt");

        String subcommand = args.length > 0 ? args[0] : null;
        boolean headless = HEADLESS_COMMANDS.contains(subcommand);

        if ("mcp".equals(subcommand)) {
            // Keep stdout pristine for JSON-RPC framing; everything else goes to stderr.
            REAL_STDOUT = System.out;
            System.setOut(System.err);
        } else {
            log.info("Starting Spring Boost MCP Server...");
        }

        SpringApplication app = new SpringApplication(SpringBoostApplication.class);
        app.setAdditionalProfiles("mcp-server");

        if (headless) {
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(Banner.Mode.OFF);
            app.setLogStartupInfo(false);
        }

        try {
            var context = app.run(args);
            // Any CLI subcommand/flag (args.length > 0) has already closed the
            // context by now via SpringApplication.exit() in BoostCommand; only
            // the true long-running server (no args) reaches this log safely.
            if (args.length == 0) {
                log.info("Spring Boost MCP Server started successfully!");
                log.info("Available tools: {}", context.getBeansOfType(com.springboost.mcp.tools.McpTool.class).size());
            }
        } catch (Exception e) {
            log.error("Failed to start Spring Boost MCP Server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
