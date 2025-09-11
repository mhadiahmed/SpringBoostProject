package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Tool to convert relative paths to absolute URLs
 * Provides URL construction with server port, context path, and SSL configuration awareness
 */
@Slf4j
@Component
public class GetAbsoluteUrlTool implements McpTool {
    
    private final Environment environment;
    private final ServletWebServerApplicationContext applicationContext;
    
    @Autowired
    public GetAbsoluteUrlTool(Environment environment, 
                             @Autowired(required = false) ServletWebServerApplicationContext applicationContext) {
        this.environment = environment;
        this.applicationContext = applicationContext;
    }
    
    @Override
    public String getName() {
        return "get-absolute-url";
    }
    
    @Override
    public String getDescription() {
        return "Convert relative paths to absolute URLs using server configuration (port, context path, SSL)";
    }
    
    @Override
    public String getCategory() {
        return "web";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "relativePath", Map.of(
                        "type", "string",
                        "description", "Relative path to convert to absolute URL",
                        "examples", Arrays.asList("/api/users", "api/users/123", "/health")
                ),
                "includeContextPath", Map.of(
                        "type", "boolean",
                        "description", "Include servlet context path in the URL",
                        "default", true
                ),
                "forceHttps", Map.of(
                        "type", "boolean",
                        "description", "Force HTTPS protocol regardless of server configuration",
                        "default", false
                ),
                "forceHttp", Map.of(
                        "type", "boolean",
                        "description", "Force HTTP protocol regardless of server configuration",
                        "default", false
                ),
                "customHost", Map.of(
                        "type", "string",
                        "description", "Use custom host instead of detected server host"
                ),
                "customPort", Map.of(
                        "type", "integer",
                        "description", "Use custom port instead of detected server port"
                ),
                "includeAlternatives", Map.of(
                        "type", "boolean",
                        "description", "Include alternative URL formats in the response",
                        "default", false
                )
        ));
        schema.put("required", Arrays.asList("relativePath"));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String relativePath = (String) params.get("relativePath");
            if (relativePath == null) {
                throw new McpToolException(getName(), "relativePath parameter is required");
            }
            
            boolean includeContextPath = (boolean) params.getOrDefault("includeContextPath", true);
            boolean forceHttps = (boolean) params.getOrDefault("forceHttps", false);
            boolean forceHttp = (boolean) params.getOrDefault("forceHttp", false);
            String customHost = (String) params.get("customHost");
            Integer customPort = params.get("customPort") != null ? 
                    ((Number) params.get("customPort")).intValue() : null;
            boolean includeAlternatives = (boolean) params.getOrDefault("includeAlternatives", false);
            
            if (forceHttps && forceHttp) {
                throw new McpToolException(getName(), "Cannot force both HTTPS and HTTP protocols");
            }
            
            Map<String, Object> result = new HashMap<>();
            
            // Get server configuration
            ServerConfig serverConfig = getServerConfiguration();
            result.put("serverConfig", serverConfig.toMap());
            
            // Build the absolute URL
            String absoluteUrl = buildAbsoluteUrl(
                    relativePath, serverConfig, includeContextPath, 
                    forceHttps, forceHttp, customHost, customPort);
            
            result.put("absoluteUrl", absoluteUrl);
            result.put("relativePath", relativePath);
            
            // Parse and validate the URL
            try {
                URL url = new URL(absoluteUrl);
                Map<String, Object> urlComponents = new HashMap<>();
                urlComponents.put("protocol", url.getProtocol());
                urlComponents.put("host", url.getHost());
                urlComponents.put("port", url.getPort() == -1 ? getDefaultPort(url.getProtocol()) : url.getPort());
                urlComponents.put("path", url.getPath());
                urlComponents.put("query", url.getQuery());
                urlComponents.put("fragment", url.getRef());
                result.put("urlComponents", urlComponents);
            } catch (MalformedURLException e) {
                result.put("urlError", "Generated URL is malformed: " + e.getMessage());
            }
            
            // Include alternatives if requested
            if (includeAlternatives) {
                result.put("alternatives", generateAlternativeUrls(relativePath, serverConfig, includeContextPath));
            }
            
            // Additional information
            result.put("metadata", Map.of(
                    "includeContextPath", includeContextPath,
                    "forceHttps", forceHttps,
                    "forceHttp", forceHttp,
                    "customHost", customHost != null,
                    "customPort", customPort != null,
                    "timestamp", System.currentTimeMillis()
            ));
            
            return result;
            
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate absolute URL: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to generate absolute URL: " + e.getMessage(), e);
        }
    }
    
    private ServerConfig getServerConfiguration() {
        ServerConfig config = new ServerConfig();
        
        // Protocol
        boolean sslEnabled = environment.getProperty("server.ssl.enabled", Boolean.class, false);
        config.protocol = sslEnabled ? "https" : "http";
        
        // Port
        Integer serverPort = environment.getProperty("server.port", Integer.class);
        if (serverPort == null) {
            try {
                if (applicationContext != null) {
                    serverPort = applicationContext.getWebServer().getPort();
                } else {
                    throw new Exception("No ServletWebServerApplicationContext available");
                }
            } catch (Exception e) {
                log.debug("Could not get port from web server: {}", e.getMessage());
                serverPort = sslEnabled ? 8443 : 8080; // Default ports
            }
        }
        config.port = serverPort;
        
        // Host
        String serverAddress = environment.getProperty("server.address");
        if (serverAddress == null || serverAddress.equals("0.0.0.0")) {
            config.host = "localhost";
        } else {
            config.host = serverAddress;
        }
        
        // Context path
        config.contextPath = environment.getProperty("server.servlet.context-path", "");
        if (!config.contextPath.isEmpty() && !config.contextPath.startsWith("/")) {
            config.contextPath = "/" + config.contextPath;
        }
        
        // Additional properties
        config.serverName = environment.getProperty("spring.application.name", "spring-application");
        
        return config;
    }
    
    private String buildAbsoluteUrl(String relativePath, ServerConfig serverConfig, 
                                   boolean includeContextPath, boolean forceHttps, boolean forceHttp,
                                   String customHost, Integer customPort) {
        
        StringBuilder url = new StringBuilder();
        
        // Protocol
        if (forceHttps) {
            url.append("https");
        } else if (forceHttp) {
            url.append("http");
        } else {
            url.append(serverConfig.protocol);
        }
        url.append("://");
        
        // Host
        url.append(customHost != null ? customHost : serverConfig.host);
        
        // Port
        int port = customPort != null ? customPort : serverConfig.port;
        String protocol = forceHttps ? "https" : (forceHttp ? "http" : serverConfig.protocol);
        
        // Only include port if it's not the default for the protocol
        if (!isDefaultPort(protocol, port)) {
            url.append(":").append(port);
        }
        
        // Context path
        if (includeContextPath && !serverConfig.contextPath.isEmpty()) {
            url.append(serverConfig.contextPath);
        }
        
        // Relative path
        String normalizedPath = normalizePath(relativePath);
        url.append(normalizedPath);
        
        return url.toString();
    }
    
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        // Remove duplicate slashes
        path = path.replaceAll("/+", "/");
        
        return path;
    }
    
    private boolean isDefaultPort(String protocol, int port) {
        return (protocol.equals("http") && port == 80) || 
               (protocol.equals("https") && port == 443);
    }
    
    private int getDefaultPort(String protocol) {
        return protocol.equals("https") ? 443 : 80;
    }
    
    private Map<String, Object> generateAlternativeUrls(String relativePath, ServerConfig serverConfig, 
                                                        boolean includeContextPath) {
        Map<String, Object> alternatives = new HashMap<>();
        
        // HTTP version
        if (serverConfig.protocol.equals("https")) {
            String httpUrl = buildAbsoluteUrl(relativePath, serverConfig, includeContextPath, 
                    false, true, null, null);
            alternatives.put("http", httpUrl);
        }
        
        // HTTPS version
        if (serverConfig.protocol.equals("http")) {
            String httpsUrl = buildAbsoluteUrl(relativePath, serverConfig, includeContextPath, 
                    true, false, null, null);
            alternatives.put("https", httpsUrl);
        }
        
        // Without context path
        if (includeContextPath && !serverConfig.contextPath.isEmpty()) {
            String withoutContext = buildAbsoluteUrl(relativePath, serverConfig, false, 
                    false, false, null, null);
            alternatives.put("withoutContextPath", withoutContext);
        }
        
        // With context path
        if (!includeContextPath && !serverConfig.contextPath.isEmpty()) {
            String withContext = buildAbsoluteUrl(relativePath, serverConfig, true, 
                    false, false, null, null);
            alternatives.put("withContextPath", withContext);
        }
        
        // Common external hosts
        alternatives.put("externalHost", buildAbsoluteUrl(relativePath, serverConfig, includeContextPath, 
                false, false, "example.com", null));
        
        // Standard ports
        alternatives.put("standardHttp", buildAbsoluteUrl(relativePath, serverConfig, includeContextPath, 
                false, true, null, 80));
        alternatives.put("standardHttps", buildAbsoluteUrl(relativePath, serverConfig, includeContextPath, 
                true, false, null, 443));
        
        return alternatives;
    }
    
    private static class ServerConfig {
        String protocol;
        String host;
        int port;
        String contextPath;
        String serverName;
        
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("protocol", protocol);
            map.put("host", host);
            map.put("port", port);
            map.put("contextPath", contextPath);
            map.put("serverName", serverName);
            map.put("baseUrl", protocol + "://" + host + ":" + port + contextPath);
            return map;
        }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "basic", Map.of(
                        "description", "Convert a simple relative path to absolute URL",
                        "parameters", Map.of("relativePath", "/api/users")
                ),
                "withoutContext", Map.of(
                        "description", "Generate URL without servlet context path",
                        "parameters", Map.of(
                                "relativePath", "/health",
                                "includeContextPath", false
                        )
                ),
                "forceHttps", Map.of(
                        "description", "Force HTTPS protocol",
                        "parameters", Map.of(
                                "relativePath", "/api/secure",
                                "forceHttps", true
                        )
                ),
                "customHost", Map.of(
                        "description", "Use custom host and port",
                        "parameters", Map.of(
                                "relativePath", "/api/data",
                                "customHost", "api.example.com",
                                "customPort", 443,
                                "forceHttps", true
                        )
                ),
                "withAlternatives", Map.of(
                        "description", "Get alternative URL formats",
                        "parameters", Map.of(
                                "relativePath", "/dashboard",
                                "includeAlternatives", true
                        )
                )
        );
    }
}
