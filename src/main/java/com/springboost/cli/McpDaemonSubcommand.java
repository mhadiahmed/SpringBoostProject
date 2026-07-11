package com.springboost.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboost.launcher.DaemonPaths;
import com.springboost.mcp.McpMessageProcessor;
import com.springboost.mcp.protocol.McpError;
import com.springboost.mcp.protocol.McpMessage;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The long-lived daemon {@link com.springboost.launcher.ThinLauncher} connects
 * new {@code mcp} stdio sessions to. Keeps one Spring context warm and serves
 * every client over a local TCP socket instead of paying context boot per
 * session. Not meant to be run directly by users — spawned automatically.
 */
@Slf4j
@CommandLine.Command(
        name = "mcp-daemon",
        description = "Internal: run the shared MCP daemon (spawned automatically by the stdio launcher)",
        hidden = true
)
public class McpDaemonSubcommand implements Callable<Integer> {

    private final McpMessageProcessor messageProcessor;
    private final ObjectMapper objectMapper;

    public McpDaemonSubcommand(McpMessageProcessor messageProcessor, ObjectMapper objectMapper) {
        this.messageProcessor = messageProcessor;
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() throws Exception {
        DaemonPaths.ensureHomeExists();

        FileChannel lockChannel = FileChannel.open(DaemonPaths.LOCK_FILE,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = lockChannel.tryLock();
        if (lock == null) {
            log.info("Another spring-boost daemon already holds the lock; exiting.");
            return 0;
        }
        // Intentionally never released: this lock's lifetime IS the daemon's
        // lifetime. The OS releases it automatically when this process exits,
        // which is how a future ThinLauncher detects the daemon has died.

        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = serverSocket.getLocalPort();
            Files.writeString(DaemonPaths.PORT_FILE, String.valueOf(port),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("spring-boost MCP daemon listening on 127.0.0.1:{}", port);

            ExecutorService connections = Executors.newCachedThreadPool();
            while (true) {
                Socket client = serverSocket.accept();
                connections.submit(() -> handleConnection(client));
            }
        }
    }

    private void handleConnection(Socket client) {
        log.info("MCP client connected: {}", client.getRemoteSocketAddress());
        boolean firstMessage = true;
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintStream out = new PrintStream(client.getOutputStream(), true, StandardCharsets.UTF_8)) {

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                long msgStart = System.nanoTime();
                McpMessage response;
                try {
                    McpMessage request = objectMapper.readValue(line, McpMessage.class);
                    response = messageProcessor.process(request);
                } catch (Exception e) {
                    response = McpMessage.createErrorResponse(null,
                            McpError.parseError("Failed to parse message: " + e.getMessage()));
                }

                if (response != null) {
                    out.println(objectMapper.writeValueAsString(response));
                }

                if (firstMessage) {
                    long msgMs = (System.nanoTime() - msgStart) / 1_000_000;
                    log.info("First MCP round-trip for this client handled in {}ms", msgMs);
                    firstMessage = false;
                }
            }
        } catch (IOException e) {
            log.debug("MCP client connection closed: {}", e.getMessage());
        }
    }
}
