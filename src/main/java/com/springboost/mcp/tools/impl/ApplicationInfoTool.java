package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Tool to retrieve comprehensive Spring Boot application information
 * Provides details about Spring Boot version, active profiles, beans, environment, and JVM
 */
@Slf4j
@Component
public class ApplicationInfoTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    private final Environment environment;
    private final BuildProperties buildProperties;
    
    @Autowired
    public ApplicationInfoTool(ApplicationContext applicationContext, 
                              Environment environment,
                              @Autowired(required = false) BuildProperties buildProperties) {
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.buildProperties = buildProperties;
    }
    
    @Override
    public String getName() {
        return "application-info";
    }
    
    @Override
    public String getDescription() {
        return "Get comprehensive information about the Spring Boot application including version, profiles, beans, environment variables, and JVM details";
    }
    
    @Override
    public String getCategory() {
        return "application";
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "includeJvm", Map.of(
                        "type", "boolean",
                        "description", "Include JVM information in the response",
                        "default", true
                ),
                "includeBeans", Map.of(
                        "type", "boolean", 
                        "description", "Include bean definitions in the response",
                        "default", false
                ),
                "includeEnvironment", Map.of(
                        "type", "boolean",
                        "description", "Include environment variables (non-sensitive only)",
                        "default", true
                ),
                "beanNameFilter", Map.of(
                        "type", "string",
                        "description", "Filter bean names by this substring (case-insensitive)"
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            boolean includeJvm = (boolean) params.getOrDefault("includeJvm", true);
            boolean includeBeans = (boolean) params.getOrDefault("includeBeans", false);
            boolean includeEnvironment = (boolean) params.getOrDefault("includeEnvironment", true);
            String beanNameFilter = (String) params.get("beanNameFilter");
            
            Map<String, Object> result = new HashMap<>();
            
            // Spring Boot information
            result.put("springBoot", getSpringBootInfo());
            
            // Application information
            result.put("application", getApplicationInfo());
            
            // Profile information
            result.put("profiles", getProfileInfo());
            
            // JVM information
            if (includeJvm) {
                result.put("jvm", getJvmInfo());
            }
            
            // Bean information
            if (includeBeans) {
                result.put("beans", getBeanInfo(beanNameFilter));
            }
            
            // Environment information (non-sensitive)
            if (includeEnvironment) {
                result.put("environment", getEnvironmentInfo());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to retrieve application info: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to retrieve application information: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> getSpringBootInfo() {
        Map<String, Object> springInfo = new HashMap<>();
        springInfo.put("version", SpringBootVersion.getVersion());
        springInfo.put("runtimeVersion", System.getProperty("java.runtime.version"));
        springInfo.put("springVersion", org.springframework.core.SpringVersion.getVersion());
        return springInfo;
    }
    
    private Map<String, Object> getApplicationInfo() {
        Map<String, Object> appInfo = new HashMap<>();
        
        // Application name
        appInfo.put("name", environment.getProperty("spring.application.name", "spring-boot-application"));
        
        // Build information if available
        if (buildProperties != null) {
            appInfo.put("artifactId", buildProperties.getArtifact());
            appInfo.put("groupId", buildProperties.getGroup());
            appInfo.put("version", buildProperties.getVersion());
            appInfo.put("buildTime", buildProperties.getTime());
        }
        
        // Server information
        appInfo.put("serverPort", environment.getProperty("server.port", "8080"));
        appInfo.put("contextPath", environment.getProperty("server.servlet.context-path", "/"));
        
        // Application startup time
        appInfo.put("startupTime", applicationContext.getStartupDate());
        
        return appInfo;
    }
    
    private Map<String, Object> getProfileInfo() {
        Map<String, Object> profileInfo = new HashMap<>();
        profileInfo.put("active", Arrays.asList(environment.getActiveProfiles()));
        profileInfo.put("default", Arrays.asList(environment.getDefaultProfiles()));
        return profileInfo;
    }
    
    private Map<String, Object> getJvmInfo() {
        Map<String, Object> jvmInfo = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        
        // Memory information
        Map<String, Object> memory = new HashMap<>();
        memory.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        memory.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        memory.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        memory.put("usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        jvmInfo.put("memory", memory);
        
        // JVM properties
        jvmInfo.put("javaVersion", System.getProperty("java.version"));
        jvmInfo.put("javaVendor", System.getProperty("java.vendor"));
        jvmInfo.put("javaHome", System.getProperty("java.home"));
        jvmInfo.put("osName", System.getProperty("os.name"));
        jvmInfo.put("osArch", System.getProperty("os.arch"));
        jvmInfo.put("processors", runtime.availableProcessors());
        
        // JVM arguments
        try {
            jvmInfo.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        } catch (Exception e) {
            log.debug("Could not retrieve JVM arguments: {}", e.getMessage());
        }
        
        return jvmInfo;
    }
    
    private Map<String, Object> getBeanInfo(String nameFilter) {
        Map<String, Object> beanInfo = new HashMap<>();
        
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        List<String> filteredBeans = new ArrayList<>();
        
        for (String beanName : beanNames) {
            if (nameFilter == null || nameFilter.isEmpty() || 
                beanName.toLowerCase().contains(nameFilter.toLowerCase())) {
                filteredBeans.add(beanName);
            }
        }
        
        beanInfo.put("totalBeans", beanNames.length);
        beanInfo.put("filteredBeans", filteredBeans);
        beanInfo.put("filterApplied", nameFilter != null && !nameFilter.isEmpty());
        
        // Group beans by package
        Map<String, Integer> packageCount = new HashMap<>();
        for (String beanName : filteredBeans) {
            try {
                Object bean = applicationContext.getBean(beanName);
                String packageName = bean.getClass().getPackage() != null ? 
                    bean.getClass().getPackage().getName() : "default";
                packageCount.merge(packageName, 1, Integer::sum);
            } catch (Exception e) {
                // Skip beans that can't be retrieved
            }
        }
        beanInfo.put("packageDistribution", packageCount);
        
        return beanInfo;
    }
    
    private Map<String, Object> getEnvironmentInfo() {
        Map<String, Object> envInfo = new HashMap<>();
        
        // Safe environment variables (excluding sensitive data)
        Set<String> safeKeys = Set.of(
                "java.version", "java.vendor", "java.home",
                "os.name", "os.arch", "os.version",
                "user.name", "user.dir", "user.timezone",
                "file.separator", "path.separator", "line.separator"
        );
        
        Map<String, String> safeSystemProps = new HashMap<>();
        for (String key : safeKeys) {
            String value = System.getProperty(key);
            if (value != null) {
                safeSystemProps.put(key, value);
            }
        }
        envInfo.put("systemProperties", safeSystemProps);
        
        // Spring-specific properties (non-sensitive)
        Map<String, String> springProps = new HashMap<>();
        String[] springKeys = {
                "spring.application.name",
                "spring.profiles.active",
                "spring.profiles.default",
                "server.port",
                "server.servlet.context-path",
                "management.endpoints.web.base-path"
        };
        
        for (String key : springKeys) {
            String value = environment.getProperty(key);
            if (value != null) {
                springProps.put(key, value);
            }
        }
        envInfo.put("springProperties", springProps);
        
        return envInfo;
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "basic", Map.of(
                        "description", "Get basic application information",
                        "parameters", Map.of()
                ),
                "withBeans", Map.of(
                        "description", "Include bean information",
                        "parameters", Map.of("includeBeans", true)
                ),
                "filteredBeans", Map.of(
                        "description", "Get beans matching a filter",
                        "parameters", Map.of(
                                "includeBeans", true,
                                "beanNameFilter", "controller"
                        )
                ),
                "minimal", Map.of(
                        "description", "Get minimal info without JVM details",
                        "parameters", Map.of(
                                "includeJvm", false,
                                "includeEnvironment", false
                        )
                )
        );
    }
}

