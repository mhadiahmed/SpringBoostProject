package com.springboost.cli;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes the AI guidelines/skills bundled in the jar into a target project's
 * .ai/ directory, mirroring Laravel Boost's boost:install / boost:update commands.
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
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> written = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String pattern : RESOURCE_PATTERNS) {
            for (Resource resource : resolver.getResources(pattern)) {
                if (!resource.isReadable()) {
                    continue;
                }

                String relativePath = relativeAiPath(resource);
                if (relativePath == null) {
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
}
