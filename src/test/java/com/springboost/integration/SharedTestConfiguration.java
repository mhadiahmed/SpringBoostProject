package com.springboost.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shared test configuration to reuse Spring context across integration tests
 * This significantly improves test performance by avoiding context reloading
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("minimal")
@TestPropertySource(properties = {
    "spring.main.banner-mode=off",
    "logging.level.root=WARN",
    "spring-boost.documentation.enabled=false",
    "spring-boot.run.jvmArguments=-Xmx512m"
})
public @interface SharedTestConfiguration {
}
