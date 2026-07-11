package com.springboost.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Fast-starting, Spring-free entry point for the stdio MCP transport.
 * {@link com.springboost.SpringBoostApplication#main} delegates straight here
 * for the {@code mcp} subcommand, before any Spring class is touched.
 *
 * <p>Connects to (auto-starting if needed) a single shared spring-boost
 * daemon process that keeps the Spring context warm, instead of paying full
 * context boot (~5s) on every stdio session spawned by an AI editor.
 */
public final class ThinLauncher {

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int STARTUP_TIMEOUT_MS = 30_000;
    private static final int POLL_INTERVAL_MS = 150;

    private ThinLauncher() {
    }

    public static void main(String[] args) throws Exception {
        DaemonPaths.ensureHomeExists();
        String key = DaemonPaths.currentIdentityKey();

        Socket socket = connectIfRunning(key);
        if (socket == null) {
            maybeSpawnDaemon(key);
            socket = waitForDaemon(key);
        }
        if (socket == null) {
            System.err.println("[spring-boost] Timed out waiting for the spring-boost daemon to start.");
            System.exit(1);
            return;
        }
        relay(socket);
    }

    private static Socket connectIfRunning(String key) {
        Integer port = readPort(key);
        if (port == null) {
            return null;
        }
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
            return socket;
        } catch (IOException e) {
            return null;
        }
    }

    private static Integer readPort(String key) {
        try {
            List<String> lines = Files.readAllLines(DaemonPaths.portFile(key), StandardCharsets.UTF_8);
            return lines.isEmpty() ? null : Integer.parseInt(lines.get(0).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static void maybeSpawnDaemon(String key) throws IOException {
        try (FileChannel channel = FileChannel.open(DaemonPaths.lockFile(key),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                // Another launcher already owns the daemon lock -- it's starting
                // (or already running) the daemon; just wait for it in waitForDaemon().
                return;
            }
            // We won the race to spawn. Release immediately: the daemon process
            // re-acquires this same lock for its own lifetime once it boots, which
            // is what future launchers will actually see held.
            lock.release();
        }

        List<String> command = buildRelaunchCommand();
        ProcessBuilder pb = new ProcessBuilder(command);
        File logFile = DaemonPaths.logFile(key).toFile();
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
        pb.redirectInput(ProcessBuilder.Redirect.from(new File(isWindows() ? "NUL" : "/dev/null")));
        pb.start();
    }

    /**
     * Clones the exact command this launcher itself was invoked with (java
     * binary, {@code -jar <path>} or {@code -cp} form, JVM flags — whatever
     * it is) and swaps its trailing {@code mcp} argument for {@code mcp-daemon},
     * so the daemon boots via the same mechanism regardless of how spring-boost
     * was launched.
     */
    private static List<String> buildRelaunchCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String javaBin = info.command().orElseGet(ThinLauncher::defaultJavaBin);
        String[] originalArgs = info.arguments().orElseGet(DaemonPaths::argsFromJavaCommand);

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.addAll(List.of(originalArgs));
        int lastIdx = command.size() - 1;
        if (lastIdx >= 1 && "mcp".equals(command.get(lastIdx))) {
            command.set(lastIdx, "mcp-daemon");
        } else {
            command.add("mcp-daemon");
        }
        return command;
    }

    private static String defaultJavaBin() {
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static Socket waitForDaemon(String key) {
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Socket socket = connectIfRunning(key);
            if (socket != null) {
                return socket;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static void relay(Socket socket) throws IOException {
        InputStream socketIn = socket.getInputStream();
        OutputStream socketOut = socket.getOutputStream();

        Thread toDaemon = new Thread(() -> {
            try {
                copy(System.in, socketOut);
            } catch (IOException ignored) {
                // stdin closed (editor ended the session) or socket closed -- either way, done.
            } finally {
                // Half-close only: stdin EOF means no more requests, but the
                // daemon's response(s) to already-sent requests may still be
                // in flight. Closing the whole socket here would race with
                // (and truncate) the read side below.
                try {
                    socket.shutdownOutput();
                } catch (IOException ignored) {
                }
            }
        }, "spring-boost-stdin-to-daemon");
        toDaemon.setDaemon(true);
        toDaemon.start();

        try {
            copy(socketIn, System.out);
        } finally {
            closeQuietly(socket);
        }
    }

    /**
     * Manual byte copy, flushing after every read -- deliberately not
     * {@link InputStream#transferTo}, which can take an OS bulk-copy fast
     * path for file-descriptor-backed streams built for regular files, not
     * interactive pipes.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            out.flush();
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
