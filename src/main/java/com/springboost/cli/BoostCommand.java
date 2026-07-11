package com.springboost.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springboost.SpringBoostApplication;
import com.springboost.config.SpringBoostProperties;
import com.springboost.mcp.McpMessageProcessor;
import com.springboost.mcp.tools.McpToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * CLI interface for Spring Boost MCP Server
 */
@Slf4j
@Component
@CommandLine.Command(
    name = "spring-boost",
    description = "Spring Boost MCP Server for AI-assisted development",
    mixinStandardHelpOptions = true,
    versionProvider = BoostCommand.VersionProvider.class
)
public class BoostCommand implements Callable<Integer>, CommandLineRunner {

    /** Reads the real build version instead of a hardcoded annotation literal that drifts on every release. */
    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] { SpringBoostApplication.getVersion() };
        }
    }
    
    @CommandLine.Option(
        names = {"-p", "--port"},
        description = "Port for MCP server (default: ${DEFAULT-VALUE})",
        defaultValue = "8080"
    )
    private int port;
    
    @CommandLine.Option(
        names = {"-h", "--host"},
        description = "Host for MCP server (default: ${DEFAULT-VALUE})",
        defaultValue = "localhost"
    )
    private String host;
    
    @CommandLine.Option(
        names = {"--list-tools"},
        description = "List available MCP tools and exit"
    )
    private boolean listTools;
    
    @CommandLine.Option(
        names = {"--validate-config"},
        description = "Validate configuration and exit"
    )
    private boolean validateConfig;

    @CommandLine.Option(
        names = {"-q", "--quiet"},
        description = "Suppress non-essential boot logs for CLI subcommands"
    )
    private boolean quiet;
    
    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private SpringBoostProperties properties;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private McpMessageProcessor messageProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GuidelinesPublisher guidelinesPublisher;

    @Override
    public void run(String... args) throws Exception {
        // Only run CLI if we have command line arguments; with none, this is the
        // long-running server (WebSocket, port 8080/28080) and should keep running.
        if (args.length > 0) {
            CommandLine cmd = new CommandLine(this);
            cmd.addSubcommand("mcp-daemon", new McpDaemonSubcommand(messageProcessor, objectMapper));
            cmd.addSubcommand("install", new InstallSubcommand(guidelinesPublisher));
            cmd.addSubcommand("update", new UpdateSubcommand(guidelinesPublisher));
            int exitCode = cmd.execute(args);
            // Always exit after a CLI subcommand/flag runs -- otherwise the app
            // keeps running (with a full web server for non-headless commands)
            // even after --list-tools/--validate-config/install/update finish.
            SpringApplication.exit(applicationContext, () -> exitCode);
        }
    }
    
    @Override
    public Integer call() throws Exception {
        try {
            if (listTools) {
                return listAvailableTools();
            }
            
            if (validateConfig) {
                return validateConfiguration();
            }
            
            // Default: show server info
            return showServerInfo();
            
        } catch (Exception e) {
            log.error("Command execution failed: {}", e.getMessage(), e);
            return 1;
        }
    }
    
    private int listAvailableTools() {
        System.out.println("\n🛠️  Available Spring Boost MCP Tools:");
        System.out.println("==========================================");

        var tools = toolRegistry.getAllTools();
        if (tools.isEmpty()) {
            System.out.println("No tools available.");
            return 0;
        }

        var coreTools = tools.stream().filter(com.springboost.mcp.tools.McpTool::isCore).toList();
        var extensionTools = tools.stream().filter(t -> !t.isCore()).toList();

        System.out.printf("\n%s CORE (Laravel Boost parity — %d tools):\n", "🎯", coreTools.size());
        printToolsByCategory(coreTools);

        if (!extensionTools.isEmpty()) {
            System.out.printf("\n%s EXTENSIONS (Spring-specific, no Boost equivalent — %d tools):\n", "🧩", extensionTools.size());
            printToolsByCategory(extensionTools);
        } else {
            System.out.println("\n🧩 EXTENSIONS: disabled (set spring-boost.mcp.tools.extensions-enabled=true to enable)");
        }

        System.out.printf("\nTotal enabled: %d tool(s)\n", tools.size());
        return 0;
    }

    private void printToolsByCategory(java.util.List<com.springboost.mcp.tools.McpTool> toolsToPrint) {
        var toolsByCategory = toolsToPrint.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    com.springboost.mcp.tools.McpTool::getCategory
                ));

        toolsByCategory.forEach((category, categoryTools) -> {
            System.out.printf("\n📂 %s:\n", category.toUpperCase());
            categoryTools.forEach(tool -> {
                System.out.printf("  • %-25s - %s\n", tool.getName(), tool.getDescription());
            });
        });
    }
    
    private int validateConfiguration() {
        System.out.println("\n🔍 Validating Spring Boost Configuration:");
        System.out.println("========================================");
        
        boolean isValid = true;
        
        // Check MCP configuration
        if (properties.getMcp().isEnabled()) {
            System.out.println("✅ MCP Server: Enabled");
            System.out.printf("   Host: %s\n", properties.getMcp().getHost());
            System.out.printf("   Port: %d\n", properties.getMcp().getPort());
            System.out.printf("   Protocol: %s\n", properties.getMcp().getProtocol());
        } else {
            System.out.println("❌ MCP Server: Disabled");
            isValid = false;
        }
        
        // Check tools configuration
        var toolsConfig = properties.getMcp().getTools();
        System.out.printf("📋 Tools Configuration:\n");
        System.out.printf("   Enabled: %s\n", toolsConfig.isEnabled() ? "✅" : "❌");
        System.out.printf("   Database Access: %s\n", toolsConfig.isDatabaseAccess() ? "✅" : "❌");
        System.out.printf("   Code Execution: %s\n", toolsConfig.isCodeExecution() ? "✅" : "❌");
        System.out.printf("   Endpoint Scanning: %s\n", toolsConfig.isEndpointScanning() ? "✅" : "❌");
        System.out.printf("   Log Access: %s\n", toolsConfig.isLogAccess() ? "✅" : "❌");
        
        // Check security configuration
        var securityConfig = properties.getSecurity();
        System.out.printf("🔒 Security Configuration:\n");
        System.out.printf("   Sandbox Enabled: %s\n", securityConfig.isSandboxEnabled() ? "✅" : "❌");
        System.out.printf("   Allowed Packages: %d\n", securityConfig.getAllowedPackages().size());
        System.out.printf("   Restricted Operations: %d\n", securityConfig.getRestrictedOperations().size());
        
        // Check tool registry
        int toolCount = toolRegistry.getAllTools().size();
        System.out.printf("🛠️  Available Tools: %d\n", toolCount);
        
        if (toolCount == 0) {
            System.out.println("⚠️  Warning: No tools are currently available");
        }
        
        System.out.printf("\n%s Configuration validation %s\n", 
                isValid ? "✅" : "❌", 
                isValid ? "passed" : "failed");
        
        return isValid ? 0 : 1;
    }
    
    private int showServerInfo() {
        System.out.println("\n🚀 Spring Boost MCP Server");
        System.out.println("==========================");
        System.out.printf("Version: %s\n", SpringBoostApplication.getVersion());
        System.out.printf("Status: %s\n", properties.getMcp().isEnabled() ? "Running" : "Stopped");
        System.out.printf("Endpoint: ws://%s:%d/mcp\n", 
                properties.getMcp().getHost(), 
                properties.getMcp().getPort());
        System.out.printf("Available Tools: %d\n", toolRegistry.getAllTools().size());
        
        System.out.println("\n📖 Usage:");
        System.out.println("  --list-tools       List all available MCP tools");
        System.out.println("  --validate-config  Validate server configuration");
        System.out.println("  --help            Show this help message");
        
        System.out.println("\n🔗 Connect your AI assistant to: ws://localhost:8080/mcp");
        
        return 0;
    }
}
