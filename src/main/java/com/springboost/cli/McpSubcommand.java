package com.springboost.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboost.SpringBoostApplication;
import com.springboost.mcp.McpMessageProcessor;
import com.springboost.mcp.protocol.McpError;
import com.springboost.mcp.protocol.McpMessage;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/**
 * Runs the MCP server over stdio: one JSON-RPC message per line in, one per
 * line out, exiting when the client closes stdin. This is the transport AI
 * editors expect for a locally registered MCP server (mirrors how
 * `php artisan boost:mcp` behaves for Laravel Boost).
 */
@CommandLine.Command(
    name = "mcp",
    description = "Run the MCP server over stdio for AI editor integration"
)
public class McpSubcommand implements Callable<Integer> {

    private final McpMessageProcessor messageProcessor;
    private final ObjectMapper objectMapper;

    public McpSubcommand(McpMessageProcessor messageProcessor, ObjectMapper objectMapper) {
        this.messageProcessor = messageProcessor;
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() throws Exception {
        PrintStream out = SpringBoostApplication.REAL_STDOUT;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

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
                out.flush();
            }
        }

        return 0;
    }
}
