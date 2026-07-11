package com.springboost.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * Shared filesystem locations for daemon coordination between {@link ThinLauncher}
 * (Spring-free, must start instantly) and {@code McpDaemonSubcommand} (Spring-side).
 *
 * <p>Deliberately shared across projects, not per-project: one daemon serves
 * every editor session that launches the same spring-boost jar. But different
 * projects can depend on different spring-boost versions (installed at
 * different paths under ~/.m2), so daemon identity is keyed off the launch
 * command itself (jar path / classpath, everything except the trailing
 * "mcp"/"mcp-daemon" subcommand) rather than one single global file — two
 * different versions never collide on the same stale daemon.
 */
public final class DaemonPaths {

    public static final Path HOME = Paths.get(System.getProperty("user.home"), ".spring-boost");

    private DaemonPaths() {
    }

    public static void ensureHomeExists() throws IOException {
        Files.createDirectories(HOME);
    }

    public static Path lockFile(String key) {
        return HOME.resolve("daemon-" + key + ".lock");
    }

    public static Path portFile(String key) {
        return HOME.resolve("daemon-" + key + ".port");
    }

    public static Path logFile(String key) {
        return HOME.resolve("daemon-" + key + ".log");
    }

    /**
     * Derives a stable identity for "this jar, launched this way" from the
     * current process's own launch arguments, minus the trailing subcommand
     * ({@code mcp} or {@code mcp-daemon}). Both {@link ThinLauncher} (which
     * computes this for itself) and the daemon it spawns (by cloning the
     * launcher's own arguments) resolve to the identical key, since the
     * daemon's launch command differs from the launcher's only in that
     * trailing argument.
     */
    public static String currentIdentityKey() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String[] args = info.arguments().orElseGet(DaemonPaths::argsFromJavaCommand);
        String command = info.command().orElse("java");

        List<String> identity = args.length <= 1
                ? List.of(command)
                : Arrays.asList(Arrays.copyOfRange(args, 0, args.length - 1));

        return sha256Hex(command + "|" + String.join("|", identity)).substring(0, 16);
    }

    /** Fallback when {@code ProcessHandle.Info.arguments()} isn't available on this platform. */
    static String[] argsFromJavaCommand() {
        String sunJavaCommand = System.getProperty("sun.java.command", "");
        String[] parts = sunJavaCommand.split("\\s+");
        return parts.length <= 1 ? new String[0] : Arrays.copyOfRange(parts, 1, parts.length);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
