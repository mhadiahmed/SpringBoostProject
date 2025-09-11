package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool to inspect REST endpoints and their mappings
 * Provides information about controllers, request mappings, parameters, and security configurations
 */
@Slf4j
@Component
public class ListEndpointsTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    
    @Autowired
    public ListEndpointsTool(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public String getName() {
        return "list-endpoints";
    }
    
    @Override
    public String getDescription() {
        return "List all REST endpoints, request mappings, parameters, return types, and controller information";
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
                "includeParameters", Map.of(
                        "type", "boolean",
                        "description", "Include detailed parameter information",
                        "default", true
                ),
                "includeReturnTypes", Map.of(
                        "type", "boolean",
                        "description", "Include return type information",
                        "default", true
                ),
                "includeAnnotations", Map.of(
                        "type", "boolean",
                        "description", "Include annotation details",
                        "default", false
                ),
                "pathFilter", Map.of(
                        "type", "string",
                        "description", "Filter endpoints by path (case-insensitive substring match)"
                ),
                "methodFilter", Map.of(
                        "type", "string",
                        "description", "Filter endpoints by HTTP method (GET, POST, etc.)"
                ),
                "controllerFilter", Map.of(
                        "type", "string",
                        "description", "Filter endpoints by controller class name"
                ),
                "groupBy", Map.of(
                        "type", "string",
                        "description", "Group results by: 'controller' (default), 'method', or 'path'",
                        "enum", Arrays.asList("controller", "method", "path"),
                        "default", "controller"
                )
        ));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            boolean includeParameters = (boolean) params.getOrDefault("includeParameters", true);
            boolean includeReturnTypes = (boolean) params.getOrDefault("includeReturnTypes", true);
            boolean includeAnnotations = (boolean) params.getOrDefault("includeAnnotations", false);
            String pathFilter = (String) params.get("pathFilter");
            String methodFilter = (String) params.get("methodFilter");
            String controllerFilter = (String) params.get("controllerFilter");
            String groupBy = (String) params.getOrDefault("groupBy", "controller");
            
            Map<String, Object> result = new HashMap<>();
            
            // Get RequestMappingHandlerMapping
            RequestMappingHandlerMapping handlerMapping = getRequestMappingHandlerMapping();
            if (handlerMapping == null) {
                result.put("message", "No RequestMappingHandlerMapping found - Web MVC not configured");
                result.put("endpointCount", 0);
                return result;
            }
            
            // Get all handler methods
            Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
            
            List<Map<String, Object>> endpoints = new ArrayList<>();
            
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
                RequestMappingInfo mappingInfo = entry.getKey();
                HandlerMethod handlerMethod = entry.getValue();
                
                Map<String, Object> endpoint = buildEndpointInfo(
                        mappingInfo, handlerMethod, includeParameters, includeReturnTypes, includeAnnotations);
                
                // Apply filters
                if (shouldIncludeEndpoint(endpoint, pathFilter, methodFilter, controllerFilter)) {
                    endpoints.add(endpoint);
                }
            }
            
            // Sort and group results
            result.put("endpoints", organizeEndpoints(endpoints, groupBy));
            result.put("endpointCount", endpoints.size());
            result.put("totalMappings", handlerMethods.size());
            result.put("groupedBy", groupBy);
            
            // Summary statistics
            result.put("summary", generateSummary(endpoints));
            result.put("timestamp", System.currentTimeMillis());
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to list endpoints: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to list endpoints: " + e.getMessage(), e);
        }
    }
    
    private RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        try {
            return applicationContext.getBean(RequestMappingHandlerMapping.class);
        } catch (Exception e) {
            log.debug("RequestMappingHandlerMapping not found: {}", e.getMessage());
            return null;
        }
    }
    
    private Map<String, Object> buildEndpointInfo(RequestMappingInfo mappingInfo, HandlerMethod handlerMethod,
                                                 boolean includeParameters, boolean includeReturnTypes, 
                                                 boolean includeAnnotations) {
        Map<String, Object> endpoint = new HashMap<>();
        
        // Basic information
        endpoint.put("controllerClass", handlerMethod.getBeanType().getSimpleName());
        endpoint.put("controllerFullName", handlerMethod.getBeanType().getName());
        endpoint.put("methodName", handlerMethod.getMethod().getName());
        
        // Request mapping information
        if (mappingInfo.getPathPatternsCondition() != null) {
            endpoint.put("paths", mappingInfo.getPathPatternsCondition().getPatterns()
                    .stream().map(Object::toString).collect(Collectors.toList()));
        } else if (mappingInfo.getPatternsCondition() != null) {
            endpoint.put("paths", new ArrayList<>(mappingInfo.getPatternsCondition().getPatterns()));
        } else {
            endpoint.put("paths", Arrays.asList("/"));
        }
        
        // HTTP methods
        if (mappingInfo.getMethodsCondition() != null) {
            endpoint.put("httpMethods", mappingInfo.getMethodsCondition().getMethods()
                    .stream().map(Enum::toString).collect(Collectors.toList()));
        } else {
            endpoint.put("httpMethods", Arrays.asList("ALL"));
        }
        
        // Consumes/Produces
        if (mappingInfo.getConsumesCondition() != null) {
            endpoint.put("consumes", mappingInfo.getConsumesCondition().getConsumableMediaTypes()
                    .stream().map(Object::toString).collect(Collectors.toList()));
        }
        
        if (mappingInfo.getProducesCondition() != null) {
            endpoint.put("produces", mappingInfo.getProducesCondition().getProducibleMediaTypes()
                    .stream().map(Object::toString).collect(Collectors.toList()));
        }
        
        // Parameters
        if (includeParameters) {
            endpoint.put("parameters", getParameterInfo(handlerMethod.getMethod()));
        }
        
        // Return type
        if (includeReturnTypes) {
            endpoint.put("returnType", getReturnTypeInfo(handlerMethod.getMethod()));
        }
        
        // Annotations
        if (includeAnnotations) {
            endpoint.put("annotations", getAnnotationInfo(handlerMethod.getMethod()));
        }
        
        // Security information (if Spring Security is present)
        endpoint.put("security", getSecurityInfo(handlerMethod));
        
        return endpoint;
    }
    
    private List<Map<String, Object>> getParameterInfo(Method method) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        
        Parameter[] methodParams = method.getParameters();
        for (Parameter param : methodParams) {
            Map<String, Object> paramInfo = new HashMap<>();
            paramInfo.put("name", param.getName());
            paramInfo.put("type", param.getType().getSimpleName());
            paramInfo.put("fullType", param.getType().getName());
            
            // Check for Spring annotations
            if (param.isAnnotationPresent(RequestParam.class)) {
                RequestParam requestParam = param.getAnnotation(RequestParam.class);
                paramInfo.put("source", "RequestParam");
                paramInfo.put("paramName", requestParam.value().isEmpty() ? requestParam.name() : requestParam.value());
                paramInfo.put("required", requestParam.required());
                paramInfo.put("defaultValue", requestParam.defaultValue().equals(ValueConstants.DEFAULT_NONE) ? null : requestParam.defaultValue());
            } else if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable pathVar = param.getAnnotation(PathVariable.class);
                paramInfo.put("source", "PathVariable");
                paramInfo.put("paramName", pathVar.value().isEmpty() ? pathVar.name() : pathVar.value());
                paramInfo.put("required", pathVar.required());
            } else if (param.isAnnotationPresent(RequestBody.class)) {
                paramInfo.put("source", "RequestBody");
                paramInfo.put("required", param.getAnnotation(RequestBody.class).required());
            } else if (param.isAnnotationPresent(RequestHeader.class)) {
                RequestHeader header = param.getAnnotation(RequestHeader.class);
                paramInfo.put("source", "RequestHeader");
                paramInfo.put("paramName", header.value().isEmpty() ? header.name() : header.value());
                paramInfo.put("required", header.required());
                paramInfo.put("defaultValue", header.defaultValue().equals(ValueConstants.DEFAULT_NONE) ? null : header.defaultValue());
            } else {
                paramInfo.put("source", "Unknown");
            }
            
            parameters.add(paramInfo);
        }
        
        return parameters;
    }
    
    private Map<String, Object> getReturnTypeInfo(Method method) {
        Map<String, Object> returnInfo = new HashMap<>();
        
        Class<?> returnType = method.getReturnType();
        returnInfo.put("type", returnType.getSimpleName());
        returnInfo.put("fullType", returnType.getName());
        returnInfo.put("isVoid", returnType.equals(Void.TYPE));
        returnInfo.put("isResponseEntity", returnType.getName().contains("ResponseEntity"));
        
        // Check for generic types
        if (method.getGenericReturnType() != null) {
            returnInfo.put("genericType", method.getGenericReturnType().getTypeName());
        }
        
        return returnInfo;
    }
    
    private List<Map<String, Object>> getAnnotationInfo(Method method) {
        List<Map<String, Object>> annotations = new ArrayList<>();
        
        for (Annotation annotation : method.getAnnotations()) {
            Map<String, Object> annotationInfo = new HashMap<>();
            annotationInfo.put("type", annotation.annotationType().getSimpleName());
            annotationInfo.put("fullType", annotation.annotationType().getName());
            
            // Extract some common annotation properties
            if (annotation instanceof RequestMapping) {
                RequestMapping rm = (RequestMapping) annotation;
                annotationInfo.put("value", Arrays.asList(rm.value()));
                annotationInfo.put("method", Arrays.stream(rm.method()).map(Enum::toString).collect(Collectors.toList()));
            }
            
            annotations.add(annotationInfo);
        }
        
        return annotations;
    }
    
    private Map<String, Object> getSecurityInfo(HandlerMethod handlerMethod) {
        Map<String, Object> security = new HashMap<>();
        
        try {
            // Check for common security annotations
            Method method = handlerMethod.getMethod();
            Class<?> controllerClass = handlerMethod.getBeanType();
            
            // Check for @Secured, @PreAuthorize, @RolesAllowed etc.
            security.put("hasSecurityAnnotations", hasSecurityAnnotations(method, controllerClass));
            
            // Note: Detailed security analysis would require Spring Security on classpath
            security.put("note", "Detailed security analysis requires Spring Security configuration");
            
        } catch (Exception e) {
            security.put("error", "Could not analyze security: " + e.getMessage());
        }
        
        return security;
    }
    
    private boolean hasSecurityAnnotations(Method method, Class<?> controllerClass) {
        // Check for common security annotation class names
        String[] securityAnnotations = {
                "org.springframework.security.access.annotation.Secured",
                "org.springframework.security.access.prepost.PreAuthorize",
                "org.springframework.security.access.prepost.PostAuthorize",
                "javax.annotation.security.RolesAllowed",
                "javax.annotation.security.PermitAll",
                "javax.annotation.security.DenyAll"
        };
        
        for (String annotationName : securityAnnotations) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) Class.forName(annotationName);
                if (method.isAnnotationPresent(annotationClass) || 
                    controllerClass.isAnnotationPresent(annotationClass)) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // Annotation not on classpath, continue
            }
        }
        
        return false;
    }
    
    private boolean shouldIncludeEndpoint(Map<String, Object> endpoint, String pathFilter, 
                                        String methodFilter, String controllerFilter) {
        
        if (pathFilter != null && !pathFilter.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<String> paths = (List<String>) endpoint.get("paths");
            boolean matchesPath = paths.stream()
                    .anyMatch(path -> path.toLowerCase().contains(pathFilter.toLowerCase()));
            if (!matchesPath) return false;
        }
        
        if (methodFilter != null && !methodFilter.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<String> methods = (List<String>) endpoint.get("httpMethods");
            boolean matchesMethod = methods.stream()
                    .anyMatch(method -> method.equalsIgnoreCase(methodFilter));
            if (!matchesMethod) return false;
        }
        
        if (controllerFilter != null && !controllerFilter.isEmpty()) {
            String controllerName = (String) endpoint.get("controllerClass");
            if (!controllerName.toLowerCase().contains(controllerFilter.toLowerCase())) {
                return false;
            }
        }
        
        return true;
    }
    
    private Object organizeEndpoints(List<Map<String, Object>> endpoints, String groupBy) {
        switch (groupBy) {
            case "method":
                return endpoints.stream()
                        .collect(Collectors.groupingBy(e -> ((List<?>) e.get("httpMethods")).get(0)));
                
            case "path":
                return endpoints.stream()
                        .collect(Collectors.groupingBy(e -> ((List<?>) e.get("paths")).get(0)));
                
            case "controller":
            default:
                return endpoints.stream()
                        .collect(Collectors.groupingBy(e -> e.get("controllerClass")));
        }
    }
    
    private Map<String, Object> generateSummary(List<Map<String, Object>> endpoints) {
        Map<String, Object> summary = new HashMap<>();
        
        // Count by HTTP method
        Map<String, Long> methodCounts = endpoints.stream()
                .flatMap(e -> ((List<String>) e.get("httpMethods")).stream())
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()));
        summary.put("methodCounts", methodCounts);
        
        // Count by controller
        Map<String, Long> controllerCounts = endpoints.stream()
                .collect(Collectors.groupingBy(e -> (String) e.get("controllerClass"), Collectors.counting()));
        summary.put("controllerCounts", controllerCounts);
        
        // Unique controllers
        summary.put("uniqueControllers", controllerCounts.size());
        
        return summary;
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "basic", Map.of(
                        "description", "List all endpoints with basic information",
                        "parameters", Map.of()
                ),
                "filtered", Map.of(
                        "description", "List endpoints matching specific criteria",
                        "parameters", Map.of(
                                "pathFilter", "api",
                                "methodFilter", "GET"
                        )
                ),
                "byController", Map.of(
                        "description", "List endpoints for a specific controller",
                        "parameters", Map.of("controllerFilter", "User")
                ),
                "detailed", Map.of(
                        "description", "Get detailed endpoint information including annotations",
                        "parameters", Map.of(
                                "includeAnnotations", true,
                                "includeParameters", true,
                                "includeReturnTypes", true
                        )
                ),
                "groupByMethod", Map.of(
                        "description", "Group endpoints by HTTP method",
                        "parameters", Map.of("groupBy", "method")
                )
        );
    }
}
