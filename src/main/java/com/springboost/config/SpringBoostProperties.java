package com.springboost.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for Spring Boost MCP Server
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring-boost")
public class SpringBoostProperties {

    @NestedConfigurationProperty
    private McpProperties mcp = new McpProperties();

    @NestedConfigurationProperty
    private DocumentationProperties documentation = new DocumentationProperties();

    @NestedConfigurationProperty
    private SecurityProperties security = new SecurityProperties();

    @NestedConfigurationProperty
    private LoggingProperties logging = new LoggingProperties();

    @Data
    public static class McpProperties {
        private boolean enabled = true;
        private int port = 8080;
        private String host = "localhost";
        private String protocol = "websocket";
        
        @NestedConfigurationProperty
        private ToolsProperties tools = new ToolsProperties();
    }

    @Data
    public static class ToolsProperties {
        private boolean enabled = true;
        private boolean databaseAccess = true;
        private boolean codeExecution = false;
        private boolean endpointScanning = true;
        private boolean logAccess = true;
    }

    @Data
    public static class DocumentationProperties {
        private boolean enabled = true;
        private String embeddingsProvider = "local";
        private int cacheSize = 1000;
        private int searchTimeout = 5000;
    }

    @Data
    public static class SecurityProperties {
        private boolean sandboxEnabled = true;
        private List<String> allowedPackages = List.of("com.springboost", "org.springframework");
        private List<String> restrictedOperations = List.of("file-system-write", "network-access");
    }

    @Data
    public static class LoggingProperties {
        private String level = "INFO";
        private String pattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";
        
        @NestedConfigurationProperty
        private FileProperties file = new FileProperties();
    }

    @Data
    public static class FileProperties {
        private boolean enabled = false;
        private String path = "logs/spring-boost.log";
    }
}
