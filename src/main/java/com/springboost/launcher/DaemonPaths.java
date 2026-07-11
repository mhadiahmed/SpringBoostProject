package com.springboost.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared filesystem locations for daemon coordination between {@link ThinLauncher}
 * (Spring-free, must start instantly) and {@code McpDaemonSubcommand} (Spring-side).
 * Deliberately global/shared, not per-project: one daemon serves every editor session.
 */
public final class DaemonPaths {

    public static final Path HOME = Paths.get(System.getProperty("user.home"), ".spring-boost");
    public static final Path LOCK_FILE = HOME.resolve("daemon.lock");
    public static final Path PORT_FILE = HOME.resolve("daemon.port");
    public static final Path LOG_FILE = HOME.resolve("daemon.log");

    private DaemonPaths() {
    }

    public static void ensureHomeExists() throws IOException {
        Files.createDirectories(HOME);
    }
}
