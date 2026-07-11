package com.springboost.autoconfigure;

import com.springboost.SpringBoostApplication;
import com.springboost.mcp.tools.McpToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that adding spring-boost as a dependency actually wires the MCP
 * tool registry into a host app's context (the fix for the "adding the
 * Maven dependency does nothing" gap) — as opposed to only compiling.
 */
class SpringBoostAutoConfigurationTest {

    // ServletWebServerFactoryAutoConfiguration is required for WebApplicationContextRunner
    // to refresh at all (it needs a real ServletWebServerFactory bean), not just to satisfy
    // @ConditionalOnWebApplication on SpringBoostAutoConfiguration itself.
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ServletWebServerFactoryAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    WebClientAutoConfiguration.class,
                    SpringBoostAutoConfiguration.class));

    @AfterEach
    void resetMode() {
        // SpringBoostApplication.currentMode is a JVM-wide static; reset it so
        // this test doesn't leak IN_PROCESS into whichever test runs next in
        // the same fork (Surefire reuses forks across this project's test classes).
        ReflectionTestUtils.setField(SpringBoostApplication.class, "currentMode",
                SpringBoostApplication.Mode.STANDALONE);
    }

    @Test
    void registersMcpToolRegistryInHostWebApplicationContext() {
        webContextRunner.run((AssertableWebApplicationContext context) -> {
            assertThat(context).hasSingleBean(McpToolRegistry.class);
            assertThat(SpringBoostApplication.getCurrentMode())
                    .isEqualTo(SpringBoostApplication.Mode.IN_PROCESS);
        });
    }

    @Test
    void skipsRegistrationWhenDisabledViaProperty() {
        webContextRunner.withPropertyValues("spring-boost.mcp.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(McpToolRegistry.class);
        });
    }

    @Test
    void skipsRegistrationForNonWebApplications() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringBoostAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(McpToolRegistry.class));
    }
}
