package com.springboost.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidelinesPublisherTest {

    private final GuidelinesPublisher publisher = new GuidelinesPublisher();

    @Test
    void publishesOnlyRelevantGuidelinesForDetectedDependencies(@TempDir Path tempDir) throws IOException {
        writePom(tempDir, "spring-boot-starter-web", "spring-boot-starter-data-jpa",
                "spring-boot-starter-thymeleaf", "h2");

        var result = publisher.publish(tempDir, GuidelinesPublisher.Mode.INSTALL, false, false);

        assertTrue(result.written().stream().anyMatch(p -> p.contains("guidelines/core/")),
                "core guidelines should always be published");
        assertTrue(result.written().stream().anyMatch(p -> p.contains("guidelines/spring-data/")),
                "spring-data version guideline should be published for a project with data-jpa");
        assertTrue(result.written().stream().anyMatch(p -> p.contains("skills/thymeleaf-development")),
                "thymeleaf skill should be published for a project with thymeleaf");

        assertFalse(result.written().stream().anyMatch(p -> p.contains("guidelines/spring-cloud/")),
                "spring-cloud guidelines should NOT be published without a spring-cloud dependency");
        assertFalse(result.written().stream().anyMatch(p -> p.contains("guidelines/spring-security/")),
                "spring-security version guidelines should NOT be published without a spring-security dependency");
        assertFalse(result.written().stream().anyMatch(p -> p.contains("ecosystem/kubernetes")),
                "kubernetes guidelines should NOT be published without any relevant dependency");
        assertFalse(result.written().stream().anyMatch(p -> p.contains("skills/spring-cloud-development")),
                "spring-cloud skill should NOT be published without a spring-cloud dependency");

        assertTrue(result.written().size() < 20,
                "should publish meaningfully fewer than the full 28-file bundle, got " + result.written().size());
    }

    @Test
    void publishesEverythingWhenAllFlagIsSet(@TempDir Path tempDir) throws IOException {
        writePom(tempDir, "spring-boot-starter-web");

        var result = publisher.publish(tempDir, GuidelinesPublisher.Mode.INSTALL, false, true);

        assertTrue(result.written().stream().anyMatch(p -> p.contains("guidelines/spring-cloud/")),
                "--all should publish spring-cloud guidelines regardless of dependencies");
    }

    private static void writePom(Path dir, String... artifactIds) throws IOException {
        StringBuilder deps = new StringBuilder();
        for (String artifactId : artifactIds) {
            deps.append("""
                    <dependency>
                        <groupId>org.example</groupId>
                        <artifactId>%s</artifactId>
                    </dependency>
                    """.formatted(artifactId));
        }
        Files.writeString(dir.resolve("pom.xml"), """
                <project>
                    <dependencies>
                    %s
                    </dependencies>
                </project>
                """.formatted(deps));
    }
}
