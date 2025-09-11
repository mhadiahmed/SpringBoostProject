package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool to list and inspect Spring Boot Actuator endpoints
 * Provides health check information, metrics collection, and endpoint details
 */
@Slf4j
@Component
public class ListActuatorEndpointsTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    private final Environment environment;
    
    @Autowired
    public ListActuatorEndpointsTool(ApplicationContext applicationContext, Environment environment) {
        this.applicationContext = applicationContext;
        this.environment = environment;
    }
    
    @Override
    public String getName() {
        return "list-actuator-endpoints";
    }
    
    @Override
    public String getDescription() {
        return "List all Spring Boot Actuator endpoints with health, metrics, and configuration information";
    }
    
    @Override
    public String getCategory() {
        return "monitoring";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "includeHealth", Map.of(
                        "type", "boolean",
                        "description", "Include detailed health information",
                        "default", true
                ),
                "includeMetrics", Map.of(
                        "type", "boolean",
                        "description", "Include system metrics",
                        "default", true
                ),
                "includeInfo", Map.of(
                        "type", "boolean",
                        "description", "Include application info",
                        "default", true
                ),
                "includeEnvironment", Map.of(
                        "type", "boolean",
                        "description", "Include environment properties",
                        "default", false
                ),
                "includeEndpointDetails", Map.of(
                        "type", "boolean",
                        "description", "Include detailed endpoint configuration",
                        "default", true
                ),
                "healthDetailsLevel", Map.of(
                        "type", "string",
                        "description", "Level of health details to include",
                        "enum", Arrays.asList("summary", "detailed", "full"),
                        "default", "detailed"
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            boolean includeHealth = (boolean) params.getOrDefault("includeHealth", true);
            boolean includeMetrics = (boolean) params.getOrDefault("includeMetrics", true);
            boolean includeInfo = (boolean) params.getOrDefault("includeInfo", true);
            boolean includeEnvironment = (boolean) params.getOrDefault("includeEnvironment", false);
            boolean includeEndpointDetails = (boolean) params.getOrDefault("includeEndpointDetails", true);
            String healthDetailsLevel = (String) params.getOrDefault("healthDetailsLevel", "detailed");
            
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", System.currentTimeMillis());
            
            // Get actuator configuration
            result.put("actuatorConfig", getActuatorConfiguration());
            
            // List available endpoints
            result.put("availableEndpoints", getAvailableEndpoints(includeEndpointDetails));
            
            // Health information
            if (includeHealth) {
                result.put("health", getHealthInformation(healthDetailsLevel));
            }
            
            // Metrics information
            if (includeMetrics) {
                result.put("metrics", getMetricsInformation());
            }
            
            // Application info
            if (includeInfo) {
                result.put("info", getApplicationInfo());
            }
            
            // Environment information
            if (includeEnvironment) {
                result.put("environment", getEnvironmentInfo());
            }
            
            // Management port and context
            result.put("managementServer", getManagementServerInfo());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to list actuator endpoints: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to list actuator endpoints: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> getActuatorConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        // Exposure configuration
        String webExposure = environment.getProperty("management.endpoints.web.exposure.include", "health");
        String jmxExposure = environment.getProperty("management.endpoints.jmx.exposure.include", "*");
        
        config.put("webExposure", Arrays.asList(webExposure.split(",")));
        config.put("jmxExposure", Arrays.asList(jmxExposure.split(",")));
        
        // Base path
        config.put("basePath", environment.getProperty("management.endpoints.web.base-path", "/actuator"));
        
        // Security configuration
        config.put("securityEnabled", environment.getProperty("management.security.enabled", Boolean.class, true));
        
        // Health configuration
        config.put("healthShowDetails", environment.getProperty("management.endpoint.health.show-details", "never"));
        config.put("healthShowComponents", environment.getProperty("management.endpoint.health.show-components", "never"));
        
        return config;
    }
    
    private List<Map<String, Object>> getAvailableEndpoints(boolean includeDetails) {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        
        // Try to get endpoints from WebEndpointsSupplier if available
        try {
            WebEndpointsSupplier webEndpointsSupplier = applicationContext.getBean(WebEndpointsSupplier.class);
            webEndpointsSupplier.getEndpoints().forEach(endpoint -> {
                Map<String, Object> endpointInfo = new HashMap<>();
                endpointInfo.put("id", endpoint.getEndpointId().toString());
                endpointInfo.put("enabled", true); // Assume enabled if present in supplier
                
                if (includeDetails) {
                    endpointInfo.put("operations", endpoint.getOperations().size());
                    endpointInfo.put("rootPath", endpoint.getRootPath());
                }
                
                endpoints.add(endpointInfo);
            });
        } catch (Exception e) {
            log.debug("Could not get endpoints from WebEndpointsSupplier: {}", e.getMessage());
            // Fallback to predefined endpoints
            endpoints.addAll(getStandardEndpoints(includeDetails));
        }
        
        if (endpoints.isEmpty()) {
            endpoints.addAll(getStandardEndpoints(includeDetails));
        }
        
        return endpoints;
    }
    
    private List<Map<String, Object>> getStandardEndpoints(boolean includeDetails) {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        
        String[] standardEndpoints = {
                "health", "info", "metrics", "env", "configprops", 
                "beans", "mappings", "loggers", "threaddump", "heapdump"
        };
        
        for (String endpointId : standardEndpoints) {
            Map<String, Object> endpoint = new HashMap<>();
            endpoint.put("id", endpointId);
            endpoint.put("enabled", isEndpointEnabled(endpointId));
            endpoint.put("path", "/actuator/" + endpointId);
            
            if (includeDetails) {
                endpoint.put("description", getEndpointDescription(endpointId));
                endpoint.put("sensitive", isEndpointSensitive(endpointId));
            }
            
            endpoints.add(endpoint);
        }
        
        return endpoints;
    }
    
    private boolean isEndpointEnabled(String endpointId) {
        // Check if endpoint is explicitly enabled/disabled
        Boolean explicitEnabled = environment.getProperty("management.endpoint." + endpointId + ".enabled", Boolean.class);
        if (explicitEnabled != null) {
            return explicitEnabled;
        }
        
        // Check if endpoint is in the exposure list
        String exposure = environment.getProperty("management.endpoints.web.exposure.include", "health");
        return exposure.contains("*") || exposure.contains(endpointId);
    }
    
    private String getEndpointDescription(String endpointId) {
        switch (endpointId) {
            case "health": return "Shows application health information";
            case "info": return "Displays arbitrary application info";
            case "metrics": return "Shows metrics information for the current application";
            case "env": return "Exposes properties from Spring's ConfigurableEnvironment";
            case "configprops": return "Displays a collated list of all @ConfigurationProperties";
            case "beans": return "Displays a complete list of all Spring beans";
            case "mappings": return "Displays a collated list of all @RequestMapping paths";
            case "loggers": return "Shows and modifies the configuration of loggers";
            case "threaddump": return "Performs a thread dump";
            case "heapdump": return "Returns a GZip compressed hprof heap dump file";
            default: return "Actuator endpoint";
        }
    }
    
    private boolean isEndpointSensitive(String endpointId) {
        return !Arrays.asList("health", "info").contains(endpointId);
    }
    
    private Map<String, Object> getHealthInformation(String detailLevel) {
        Map<String, Object> health = new HashMap<>();
        
        try {
            HealthEndpoint healthEndpoint = applicationContext.getBean(HealthEndpoint.class);
            HealthComponent healthComponent = healthEndpoint.health();
            
            health.put("status", healthComponent.getStatus().getCode());
            
            if ("detailed".equals(detailLevel) || "full".equals(detailLevel)) {
                // Note: getDetails() method may not be available in all Spring Boot versions
                try {
                    health.put("components", "Health details available through Spring Boot Actuator endpoint");
                } catch (Exception e) {
                    log.debug("Could not access health details: {}", e.getMessage());
                }
            }
            
            if ("full".equals(detailLevel)) {
                health.put("groups", getHealthGroups());
            }
            
        } catch (Exception e) {
            log.debug("Could not get health information: {}", e.getMessage());
            health.put("status", "UNKNOWN");
            health.put("error", "Health endpoint not available: " + e.getMessage());
        }
        
        return health;
    }
    
    private Map<String, Object> getHealthGroups() {
        Map<String, Object> groups = new HashMap<>();
        
        // Try to get configured health groups
        String[] groupNames = environment.getProperty("management.endpoint.health.group", String[].class, new String[0]);
        for (String groupName : groupNames) {
            groups.put(groupName, Map.of(
                    "include", environment.getProperty("management.endpoint.health.group." + groupName + ".include", ""),
                    "exclude", environment.getProperty("management.endpoint.health.group." + groupName + ".exclude", "")
            ));
        }
        
        return groups;
    }
    
    private Map<String, Object> getMetricsInformation() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            MetricsEndpoint metricsEndpoint = applicationContext.getBean(MetricsEndpoint.class);
            
            // Get list of available metrics (simplified for compatibility)
            metrics.put("availableMetrics", "Available through /actuator/metrics endpoint");
            metrics.put("metricsCount", "Check /actuator/metrics for full list");
            
            // Get system metrics
            metrics.put("systemMetrics", getSystemMetrics());
            
            // Get JVM metrics
            metrics.put("jvmMetrics", getJvmMetrics());
            
        } catch (Exception e) {
            log.debug("Could not get metrics information: {}", e.getMessage());
            metrics.put("error", "Metrics endpoint not available: " + e.getMessage());
            // Fallback to basic system metrics
            metrics.put("systemMetrics", getBasicSystemMetrics());
        }
        
        return metrics;
    }
    
    private Map<String, Object> getSystemMetrics() {
        Map<String, Object> systemMetrics = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        systemMetrics.put("processors", runtime.availableProcessors());
        systemMetrics.put("totalMemory", runtime.totalMemory());
        systemMetrics.put("freeMemory", runtime.freeMemory());
        systemMetrics.put("maxMemory", runtime.maxMemory());
        systemMetrics.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        
        return systemMetrics;
    }
    
    private Map<String, Object> getJvmMetrics() {
        Map<String, Object> jvmMetrics = new HashMap<>();
        
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        
        jvmMetrics.put("uptime", runtimeMXBean.getUptime());
        jvmMetrics.put("startTime", runtimeMXBean.getStartTime());
        jvmMetrics.put("vmName", runtimeMXBean.getVmName());
        jvmMetrics.put("vmVersion", runtimeMXBean.getVmVersion());
        jvmMetrics.put("vmVendor", runtimeMXBean.getVmVendor());
        
        // Memory information
        jvmMetrics.put("heapMemoryUsed", memoryMXBean.getHeapMemoryUsage().getUsed());
        jvmMetrics.put("heapMemoryMax", memoryMXBean.getHeapMemoryUsage().getMax());
        jvmMetrics.put("nonHeapMemoryUsed", memoryMXBean.getNonHeapMemoryUsage().getUsed());
        
        return jvmMetrics;
    }
    
    private Map<String, Object> getBasicSystemMetrics() {
        Map<String, Object> basicMetrics = new HashMap<>();
        basicMetrics.put("note", "Basic metrics only - full metrics endpoint not available");
        basicMetrics.putAll(getSystemMetrics());
        return basicMetrics;
    }
    
    private Map<String, Object> getApplicationInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            InfoEndpoint infoEndpoint = applicationContext.getBean(InfoEndpoint.class);
            info.putAll(infoEndpoint.info());
        } catch (Exception e) {
            log.debug("Could not get info from InfoEndpoint: {}", e.getMessage());
            // Fallback to basic application info
            info.put("app", Map.of(
                    "name", environment.getProperty("spring.application.name", "spring-boost"),
                    "description", "Spring Boost MCP Server",
                    "version", "0.1.0-SNAPSHOT"
            ));
        }
        
        return info;
    }
    
    private Map<String, Object> getEnvironmentInfo() {
        Map<String, Object> envInfo = new HashMap<>();
        
        // Active profiles
        envInfo.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        envInfo.put("defaultProfiles", Arrays.asList(environment.getDefaultProfiles()));
        
        // Key properties (non-sensitive)
        Map<String, String> keyProperties = new HashMap<>();
        keyProperties.put("spring.application.name", environment.getProperty("spring.application.name"));
        keyProperties.put("server.port", environment.getProperty("server.port"));
        keyProperties.put("management.endpoints.web.base-path", environment.getProperty("management.endpoints.web.base-path"));
        keyProperties.put("spring.datasource.url", maskSensitiveValue(environment.getProperty("spring.datasource.url")));
        
        envInfo.put("keyProperties", keyProperties);
        
        return envInfo;
    }
    
    private String maskSensitiveValue(String value) {
        if (value == null) return null;
        if (value.length() <= 10) return "***";
        return value.substring(0, 5) + "***" + value.substring(value.length() - 2);
    }
    
    private Map<String, Object> getManagementServerInfo() {
        Map<String, Object> managementInfo = new HashMap<>();
        
        // Management port
        String managementPort = environment.getProperty("management.server.port");
        if (managementPort != null) {
            managementInfo.put("port", managementPort);
            managementInfo.put("separatePort", true);
        } else {
            managementInfo.put("port", environment.getProperty("server.port", "8080"));
            managementInfo.put("separatePort", false);
        }
        
        // Management context path
        managementInfo.put("contextPath", environment.getProperty("management.server.servlet.context-path", ""));
        managementInfo.put("basePath", environment.getProperty("management.endpoints.web.base-path", "/actuator"));
        
        // SSL configuration
        managementInfo.put("sslEnabled", environment.getProperty("management.server.ssl.enabled", Boolean.class, false));
        
        return managementInfo;
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "basic", Map.of(
                        "description", "Get basic actuator endpoint information",
                        "parameters", Map.of()
                ),
                "healthOnly", Map.of(
                        "description", "Get only health information",
                        "parameters", Map.of(
                                "includeMetrics", false,
                                "includeInfo", false,
                                "includeEndpointDetails", false
                        )
                ),
                "detailedHealth", Map.of(
                        "description", "Get detailed health information",
                        "parameters", Map.of(
                                "healthDetailsLevel", "full",
                                "includeHealth", true
                        )
                ),
                "metricsOnly", Map.of(
                        "description", "Get only metrics information",
                        "parameters", Map.of(
                                "includeHealth", false,
                                "includeInfo", false,
                                "includeMetrics", true
                        )
                ),
                "withEnvironment", Map.of(
                        "description", "Include environment information",
                        "parameters", Map.of("includeEnvironment", true)
                )
        );
    }
}
