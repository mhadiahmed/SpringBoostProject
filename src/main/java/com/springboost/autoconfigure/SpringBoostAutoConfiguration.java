package com.springboost.autoconfigure;

import com.springboost.SpringBoostApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Activates spring-boost's MCP tool registry and {@code /mcp} WebSocket
 * endpoint inside a host Spring Boot application that declares spring-boost
 * as a Maven/Gradle dependency ("IN_PROCESS" mode) — as opposed to running
 * spring-boost as its own {@code java -jar spring-boost.jar mcp} process
 * ("STANDALONE" mode), which has no visibility into the host app's beans.
 *
 * <p>Deliberately scans only {@code com.springboost.mcp}, {@code .config},
 * and {@code .docs} — not {@code com.springboost.cli} (picocli commands
 * meant for the standalone jar's own {@code main()}, not a host web app).
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "spring-boost.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {"com.springboost.mcp", "com.springboost.config", "com.springboost.docs"})
public class SpringBoostAutoConfiguration {

    @PostConstruct
    void markInProcess() {
        SpringBoostApplication.activateInProcessMode();
    }
}
