package com.springboost.cli;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publishes the AI guidelines/skills bundled in the jar into a target project's
 * .ai/ directory, mirroring Laravel Boost's boost:install / boost:update commands.
 *
 * When {@code all=true} (the default for {@code install}), only guidelines
 * relevant to the target project's detected dependencies are published.
 * Pass {@code --all} to publish everything regardless.
 */
@Component
public class GuidelinesPublisher {

    private static final String[] RESOURCE_PATTERNS = {
            "classpath*:.ai/guidelines/**/*.md",
            "classpath*:.ai/skills/**/*.md"
    };

    public enum Mode {
        /** Write files that don't exist yet at the target; never overwrite unless force=true. */
        INSTALL,
        /** Overwrite files that already exist at the target; never create new ones. */
        UPDATE,
        /** Overwrite existing files and create any missing ones. */
        UPDATE_DISCOVER
    }

    public record Result(List<String> written, List<String> skipped) {
    }

    public Result publish(Path targetProjectDir, Mode mode, boolean force) throws IOException {
        return publish(targetProjectDir, mode, force, false);
    }

    public Result publish(Path targetProjectDir, Mode mode, boolean force, boolean all) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> written = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        // Detect project dependencies to decide which guidelines are relevant
        Set<String> relevantCategories = all ? null : detectRelevantCategories(targetProjectDir);

        for (String pattern : RESOURCE_PATTERNS) {
            for (Resource resource : resolver.getResources(pattern)) {
                if (!resource.isReadable()) {
                    continue;
                }

                String relativePath = relativeAiPath(resource);
                if (relativePath == null) {
                    continue;
                }

                // Filter by relevance unless --all is used
                if (relevantCategories != null && !isRelevant(relativePath, relevantCategories)) {
                    skipped.add(relativePath);
                    continue;
                }

                Path target = targetProjectDir.resolve(relativePath);
                boolean exists = Files.exists(target);
                boolean shouldWrite = switch (mode) {
                    case INSTALL -> !exists || force;
                    case UPDATE -> exists;
                    case UPDATE_DISCOVER -> true;
                };

                if (!shouldWrite) {
                    skipped.add(relativePath);
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                written.add(relativePath);
            }
        }

        return new Result(written, skipped);
    }

    private static String relativeAiPath(Resource resource) throws IOException {
        String uri = resource.getURI().toString();
        int idx = uri.indexOf(".ai/");
        return idx < 0 ? null : uri.substring(idx);
    }

    // -------------------------------------------------------------------------
    // Dependency detection
    // -------------------------------------------------------------------------

    /**
     * Detect relevant guideline categories by scanning the target project's
     * build file ({@code pom.xml} or {@code build.gradle}/{@code build.gradle.kts}).
     * Returns {@code null} if no build file is found (publish everything).
     */
    static Set<String> detectRelevantCategories(Path targetDir) {
        Set<String> deps = new HashSet<>();

        Path pomXml = targetDir.resolve("pom.xml");
        Path buildGradle = targetDir.resolve("build.gradle");
        Path buildGradleKts = targetDir.resolve("build.gradle.kts");

        if (Files.exists(pomXml)) {
            deps = parsePomDependencies(pomXml);
        } else if (Files.exists(buildGradle)) {
            deps = parseGradleDependencies(buildGradle);
        } else if (Files.exists(buildGradleKts)) {
            deps = parseGradleDependencies(buildGradleKts);
        }

        if (deps.isEmpty()) {
            return null; // Can't detect — publish everything
        }

        return mapDependenciesToCategories(deps);
    }

    /**
     * Parse a pom.xml and extract the artifact IDs of direct dependencies.
     */
    private static Set<String> parsePomDependencies(Path pomXml) {
        Set<String> artifacts = new HashSet<>();
        try {
            String content = Files.readString(pomXml);
            // Match <artifactId> inside <dependency> blocks
            Matcher m = Pattern.compile("<dependency>[\\s\\S]*?<artifactId>([^<]+)</artifactId>[\\s\\S]*?</dependency>",
                    Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                artifacts.add(m.group(1).trim().toLowerCase());
            }
        } catch (IOException e) {
            // If we can't read pom.xml, return empty and fall back to publishing all
        }
        return artifacts;
    }

    /**
     * Parse a build.gradle or build.gradle.kts file and extract dependency identifiers.
     */
    private static Set<String> parseGradleDependencies(Path gradleFile) {
        Set<String> artifacts = new HashSet<>();
        try {
            String content = Files.readString(gradleFile);
            // Match patterns like: implementation "org.springframework.boot:spring-boot-starter-web:3.2.0"
            // or: implementation("org.springframework.boot:spring-boot-starter-web")
            // or: implementation 'group:artifact:version'
            Matcher m = Pattern.compile(
                    "(?:implementation|api|compile|runtimeOnly|testImplementation)\\s*[(\"']([^:\"']+):([^:\"']+)['\"\\)]",
                    Pattern.MULTILINE).matcher(content);
            while (m.find()) {
                artifacts.add(m.group(2).trim().toLowerCase());
            }
        } catch (IOException e) {
            // fall back to publishing everything
        }
        return artifacts;
    }

    /**
     * Map detected Maven/Gradle artifact IDs to guideline categories.
     * Returns the set of guideline subdirectory names that are relevant.
     */
    private static Set<String> mapDependenciesToCategories(Set<String> artifacts) {
        Set<String> categories = new HashSet<>();
        // Always include core guidelines
        categories.add("core");

        for (String artifact : artifacts) {
            // Spring Boot Web
            if (artifact.contains("spring-boot-starter-web") || artifact.contains("spring-webmvc")
                    || artifact.contains("spring-boot-starter-json")) {
                categories.add("core");
                categories.add("spring-boot");
            }
            // Spring Boot WebFlux
            if (artifact.contains("spring-boot-starter-webflux") || artifact.contains("spring-webflux")) {
                categories.add("core");
                categories.add("spring-boot");
            }
            // Spring Data JPA
            if (artifact.contains("spring-boot-starter-data-jpa") || artifact.contains("spring-data-jpa")
                    || artifact.contains("spring-data-commons")) {
                categories.add("spring-data");
                categories.add("ecosystem/databases");
            }
            // Spring Security
            if (artifact.contains("spring-boot-starter-security") || artifact.contains("spring-security")) {
                categories.add("spring-security");
            }
            // Spring Boot Actuator
            if (artifact.contains("spring-boot-starter-actuator")) {
                categories.add("ecosystem/monitoring");
            }
            // Database drivers
            if (artifact.contains("postgresql") || artifact.contains("mysql") || artifact.contains("h2")
                    || artifact.contains("mariadb") || artifact.contains("oracle")
                    || artifact.contains("sqlserver") || artifact.contains("hsqldb")
                    || artifact.contains("mongodb") || artifact.contains("redis")
                    || artifact.contains("spring-data-mongodb") || artifact.contains("spring-data-redis")) {
                categories.add("ecosystem/databases");
                categories.add("spring-data");
            }
            // Kafka / Messaging
            if (artifact.contains("kafka") || artifact.contains("rabbit") || artifact.contains("activemq")
                    || artifact.contains("spring-jms") || artifact.contains("spring-amqp")) {
                categories.add("ecosystem/messaging");
            }
            // Spring Cloud
            if (artifact.contains("spring-cloud")) {
                categories.add("spring-cloud");
            }
            // Docker support (typically no direct dependency, but check for spring-boot-buildpacks)
            if (artifact.contains("buildpacks") || artifact.contains("docker-compose")) {
                categories.add("ecosystem/docker");
            }
            // Testing
            if (artifact.contains("spring-boot-starter-test") || artifact.contains("testcontainers")
                    || artifact.contains("mockito") || artifact.contains("junit")) {
                categories.add("testing");
            }
            // Spring Batch
            if (artifact.contains("spring-boot-starter-batch") || artifact.contains("spring-batch")) {
                categories.add("spring-batch");
            }
            // Spring Web Services
            if (artifact.contains("spring-boot-starter-web-services")) {
                categories.add("core");
            }
            // Cache
            if (artifact.contains("spring-boot-starter-cache") || artifact.contains("ehcache")
                    || artifact.contains("caffeine") || artifact.contains("redis")) {
                categories.add("ecosystem/monitoring");
            }
            // Thymeleaf
            if (artifact.contains("thymeleaf")) {
                categories.add("thymeleaf");
            }
        }

        return categories;
    }

    /**
     * Maps each skill's directory name to the category token that makes it
     * relevant. A skill is published only if its mapped category is present
     * in the detected set -- unlike guidelines, skills have no "always
     * relevant" bucket except mcp-development (which documents spring-boost
     * itself, not the target app's stack).
     */
    private static final Map<String, String> SKILL_CATEGORY = Map.ofEntries(
            Map.entry("spring-data-jpa-development", "spring-data"),
            Map.entry("spring-security-development", "spring-security"),
            Map.entry("spring-webflux-development", "webflux"),
            Map.entry("testcontainers-testing", "testing"),
            Map.entry("spring-batch-development", "spring-batch"),
            Map.entry("spring-cloud-development", "spring-cloud"),
            Map.entry("thymeleaf-development", "thymeleaf"),
            Map.entry("kafka-messaging-development", "ecosystem/messaging"),
            Map.entry("actuator-observability-development", "ecosystem/monitoring"),
            Map.entry("mcp-development", "core")
    );

    /**
     * Check if a guideline/skill file path is relevant to the detected
     * categories, using exact path-segment matches -- not a substring search.
     * A substring check breaks here because every versioned guideline file is
     * literally named "core.md" regardless of framework (e.g.
     * spring-security/5.x/core.md), so matching "core" as a substring would
     * wrongly include almost everything.
     */
    private static boolean isRelevant(String relativePath, Set<String> relevantCategories) {
        String normalized = relativePath.replace('\\', '/');

        int skillsIdx = normalized.indexOf(".ai/skills/");
        if (skillsIdx >= 0) {
            String afterSkills = normalized.substring(skillsIdx + ".ai/skills/".length());
            String skillName = afterSkills.contains("/") ? afterSkills.substring(0, afterSkills.indexOf('/')) : afterSkills;
            String mappedCategory = SKILL_CATEGORY.get(skillName);
            return mappedCategory != null && relevantCategories.contains(mappedCategory);
        }

        int guidelinesIdx = normalized.indexOf(".ai/guidelines/");
        String relToGuidelines = guidelinesIdx >= 0
                ? normalized.substring(guidelinesIdx + ".ai/guidelines/".length())
                : normalized;

        for (String category : relevantCategories) {
            String catLower = category.toLowerCase();
            if (relToGuidelines.equals(catLower) || relToGuidelines.startsWith(catLower + "/")) {
                return true;
            }
        }

        return false;
    }
}
