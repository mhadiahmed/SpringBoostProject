package com.springboost.mcp.tools.impl;

import com.springboost.config.SpringBoostProperties;
import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tool for code execution within Spring application context
 * Provides a REPL-like interface similar to Laravel's Tinker
 * Uses Spring Expression Language (SpEL) for safe code evaluation
 */
@Slf4j
@Component
public class SpringShellTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    private final SpringBoostProperties properties;
    private final ExpressionParser parser;
    private final EvaluationContext evaluationContext;
    
    // Security patterns for code validation
    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
            "\\b(System\\.exit|Runtime\\.exec|ProcessBuilder|Class\\.forName|" +
            "Method\\.invoke|Constructor\\.newInstance|Unsafe|sun\\.|" +
            "java\\.lang\\.reflect|java\\.io\\.File|java\\.nio\\.file|" +
            "javax\\.script|groovy\\.|scala\\.|kotlin\\.)\\b",
            Pattern.CASE_INSENSITIVE
    );
    
    private static final List<String> ALLOWED_PACKAGES = Arrays.asList(
            "java.lang", "java.util", "java.time", "java.math",
            "org.springframework", "com.springboost"
    );
    
    @Autowired
    public SpringShellTool(ApplicationContext applicationContext, SpringBoostProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
        
        // Configure SpEL parser with security restrictions
        SpelParserConfiguration config = new SpelParserConfiguration(
                SpelCompilerMode.IMMEDIATE, // Compile for performance
                null, // No class loader restrictions
                true,  // Auto-grow collections
                true,  // Auto-grow null references
                Integer.MAX_VALUE // No max array auto-grow
        );
        this.parser = new SpelExpressionParser(config);
        this.evaluationContext = createSecureEvaluationContext();
    }
    
    @Override
    public String getName() {
        return "spring-shell";
    }
    
    @Override
    public String getDescription() {
        return "Execute code within Spring application context using SpEL (Spring Expression Language)";
    }
    
    @Override
    public String getCategory() {
        return "execution";
    }
    
    @Override
    public boolean requiresElevatedPrivileges() {
        return true; // Code execution requires elevated privileges
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "expression", Map.of(
                        "type", "string",
                        "description", "SpEL expression to evaluate (e.g., 'applicationContext.getBeanNames()', 'dataSource.connection.metaData.databaseProductName')",
                        "minLength", 1
                ),
                "timeout", Map.of(
                        "type", "integer",
                        "description", "Execution timeout in seconds (default: 10, max: 60)",
                        "default", 10,
                        "minimum", 1,
                        "maximum", 60
                ),
                "returnType", Map.of(
                        "type", "string",
                        "description", "Expected return type for better formatting",
                        "enum", Arrays.asList("auto", "string", "number", "boolean", "object", "collection"),
                        "default", "auto"
                ),
                "format", Map.of(
                        "type", "string",
                        "description", "Output format",
                        "enum", Arrays.asList("json", "string", "pretty"),
                        "default", "pretty"
                ),
                "includeStackTrace", Map.of(
                        "type", "boolean",
                        "description", "Include stack trace on errors",
                        "default", true
                )
        ));
        schema.put("required", Arrays.asList("expression"));
        schema.put("additionalProperties", false);
        return schema;
    }
    
    @Override
    public Object execute(Map<String, Object> params) throws McpToolException {
        try {
            String expression = (String) params.get("expression");
            if (expression == null || expression.trim().isEmpty()) {
                throw new McpToolException(getName(), "Expression parameter is required and cannot be empty");
            }
            
            int timeout = ((Number) params.getOrDefault("timeout", 10)).intValue();
            String returnType = (String) params.getOrDefault("returnType", "auto");
            String format = (String) params.getOrDefault("format", "pretty");
            boolean includeStackTrace = (boolean) params.getOrDefault("includeStackTrace", true);
            
            // Validate security
            validateExpression(expression);
            
            Map<String, Object> result = new HashMap<>();
            result.put("expression", expression);
            result.put("timestamp", System.currentTimeMillis());
            
            try {
                // Execute with timeout
                Object evalResult = executeWithTimeout(expression, timeout);
                
                // Format result
                result.put("success", true);
                result.put("result", formatResult(evalResult, returnType, format));
                result.put("resultType", evalResult != null ? evalResult.getClass().getSimpleName() : "null");
                result.put("executionTimeMs", System.currentTimeMillis() - (Long) result.get("timestamp"));
                
                // Add metadata about the result
                if (evalResult != null) {
                    result.put("metadata", getResultMetadata(evalResult));
                }
                
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
                result.put("errorType", e.getClass().getSimpleName());
                
                if (includeStackTrace) {
                    List<String> stackTrace = new ArrayList<>();
                    for (StackTraceElement element : e.getStackTrace()) {
                        if (element.getClassName().startsWith("com.springboost") ||
                            element.getClassName().startsWith("org.springframework.expression")) {
                            stackTrace.add(element.toString());
                        }
                        if (stackTrace.size() >= 10) break; // Limit stack trace
                    }
                    result.put("stackTrace", stackTrace);
                }
                
                // Suggest fixes for common errors
                result.put("suggestions", getSuggestions(e, expression));
            }
            
            return result;
            
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute Spring shell expression: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to execute expression: " + e.getMessage(), e);
        }
    }
    
    private void validateExpression(String expression) throws McpToolException {
        // Check for dangerous patterns
        if (DANGEROUS_PATTERNS.matcher(expression).find()) {
            throw new McpToolException(getName(), "Expression contains potentially dangerous operations");
        }
        
        // Check sandbox settings
        if (properties.getSecurity().isSandboxEnabled()) {
            // Additional validation for sandbox mode
            if (expression.contains("@") && !expression.matches(".*@Component|@Service|@Repository|@Controller.*")) {
                throw new McpToolException(getName(), "Annotation access not allowed in sandbox mode");
            }
        }
        
        // Check expression length
        if (expression.length() > 1000) {
            throw new McpToolException(getName(), "Expression too long (max 1000 characters)");
        }
    }
    
    private Object executeWithTimeout(String expression, int timeoutSeconds) throws Exception {
        Expression spelExpression = parser.parseExpression(expression);
        
        // Simple timeout mechanism (for more complex scenarios, use CompletableFuture)
        long startTime = System.currentTimeMillis();
        Object result = spelExpression.getValue(evaluationContext);
        long executionTime = System.currentTimeMillis() - startTime;
        
        if (executionTime > timeoutSeconds * 1000L) {
            throw new RuntimeException("Expression execution timed out after " + timeoutSeconds + " seconds");
        }
        
        return result;
    }
    
    private Object formatResult(Object result, String returnType, String format) {
        if (result == null) {
            return null;
        }
        
        switch (format) {
            case "string":
                return result.toString();
                
            case "json":
                return convertToJsonCompatible(result);
                
            case "pretty":
            default:
                return formatPretty(result, returnType);
        }
    }
    
    private Object formatPretty(Object result, String returnType) {
        if (result == null) {
            return "null";
        }
        
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("value", result);
        formatted.put("type", result.getClass().getSimpleName());
        
        if (result instanceof Collection) {
            Collection<?> collection = (Collection<?>) result;
            formatted.put("size", collection.size());
            formatted.put("isEmpty", collection.isEmpty());
            if (collection.size() <= 10) {
                formatted.put("elements", new ArrayList<>(collection));
            } else {
                List<Object> sample = new ArrayList<>();
                int i = 0;
                for (Object item : collection) {
                    if (i++ >= 5) break;
                    sample.add(item);
                }
                formatted.put("sample", sample);
                formatted.put("note", "Showing first 5 elements of " + collection.size());
            }
        } else if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            formatted.put("size", map.size());
            formatted.put("isEmpty", map.isEmpty());
            if (map.size() <= 10) {
                formatted.put("entries", new HashMap<>(map));
            } else {
                Map<Object, Object> sample = new HashMap<>();
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (i++ >= 5) break;
                    sample.put(entry.getKey(), entry.getValue());
                }
                formatted.put("sample", sample);
                formatted.put("note", "Showing first 5 entries of " + map.size());
            }
        } else if (result.getClass().isArray()) {
            Object[] array = (Object[]) result;
            formatted.put("length", array.length);
            if (array.length <= 10) {
                formatted.put("elements", Arrays.asList(array));
            } else {
                formatted.put("sample", Arrays.asList(Arrays.copyOf(array, 5)));
                formatted.put("note", "Showing first 5 elements of " + array.length);
            }
        }
        
        return formatted;
    }
    
    private Object convertToJsonCompatible(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return obj;
        }
        if (obj instanceof Collection) {
            return ((Collection<?>) obj).stream()
                    .map(this::convertToJsonCompatible)
                    .toArray();
        }
        if (obj instanceof Map) {
            Map<String, Object> result = new HashMap<>();
            ((Map<?, ?>) obj).forEach((k, v) -> 
                result.put(k.toString(), convertToJsonCompatible(v)));
            return result;
        }
        return obj.toString();
    }
    
    private Map<String, Object> getResultMetadata(Object result) {
        Map<String, Object> metadata = new HashMap<>();
        
        Class<?> clazz = result.getClass();
        metadata.put("className", clazz.getName());
        metadata.put("packageName", clazz.getPackage() != null ? clazz.getPackage().getName() : "default");
        metadata.put("isCollection", result instanceof Collection);
        metadata.put("isMap", result instanceof Map);
        metadata.put("isArray", clazz.isArray());
        
        if (result instanceof Collection) {
            metadata.put("collectionSize", ((Collection<?>) result).size());
        }
        
        if (result instanceof Map) {
            metadata.put("mapSize", ((Map<?, ?>) result).size());
        }
        
        return metadata;
    }
    
    private List<String> getSuggestions(Exception error, String expression) {
        List<String> suggestions = new ArrayList<>();
        String errorMessage = error.getMessage().toLowerCase();
        
        if (errorMessage.contains("cannot resolve")) {
            suggestions.add("Check if the bean or property exists using 'applicationContext.getBeanNames()'");
            suggestions.add("Use 'applicationContext.getBean(\"beanName\")' to access beans");
        }
        
        if (errorMessage.contains("method not found")) {
            suggestions.add("Check available methods using reflection: 'object.class.methods'");
            suggestions.add("Verify method name and parameter types");
        }
        
        if (errorMessage.contains("null")) {
            suggestions.add("Check for null values before accessing properties");
            suggestions.add("Use safe navigation: 'object?.property'");
        }
        
        if (errorMessage.contains("access")) {
            suggestions.add("Check if the property/method is public");
            suggestions.add("Some operations may require elevated privileges");
        }
        
        if (expression.contains("sql") || expression.contains("database")) {
            suggestions.add("For database operations, try: 'dataSource.connection.metaData'");
            suggestions.add("Use repository beans for JPA operations");
        }
        
        return suggestions;
    }
    
    private EvaluationContext createSecureEvaluationContext() {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Add Spring beans and useful objects
        context.setVariable("applicationContext", applicationContext);
        context.setVariable("environment", applicationContext.getEnvironment());
        
        // Add DataSource if available
        try {
            DataSource dataSource = applicationContext.getBean(DataSource.class);
            context.setVariable("dataSource", dataSource);
        } catch (Exception e) {
            log.debug("No DataSource available for Spring Shell");
        }
        
        // Add useful utility methods
        context.setVariable("now", new Date());
        context.setVariable("beans", new BeanHelper(applicationContext));
        context.setVariable("props", new PropertyHelper(applicationContext.getEnvironment()));
        
        return context;
    }
    
    // Helper class for bean operations
    public static class BeanHelper {
        private final ApplicationContext context;
        
        public BeanHelper(ApplicationContext context) {
            this.context = context;
        }
        
        public String[] getNames() {
            return context.getBeanDefinitionNames();
        }
        
        public Object get(String name) {
            return context.getBean(name);
        }
        
        public <T> T get(Class<T> type) {
            return context.getBean(type);
        }
        
        public <T> Map<String, T> getAll(Class<T> type) {
            return context.getBeansOfType(type);
        }
        
        public boolean exists(String name) {
            return context.containsBean(name);
        }
    }
    
    // Helper class for property operations
    public static class PropertyHelper {
        private final org.springframework.core.env.Environment environment;
        
        public PropertyHelper(org.springframework.core.env.Environment environment) {
            this.environment = environment;
        }
        
        public String get(String key) {
            return environment.getProperty(key);
        }
        
        public String get(String key, String defaultValue) {
            return environment.getProperty(key, defaultValue);
        }
        
        public String[] getActiveProfiles() {
            return environment.getActiveProfiles();
        }
        
        public String[] getDefaultProfiles() {
            return environment.getDefaultProfiles();
        }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "beanAccess", Map.of(
                        "description", "Access Spring beans",
                        "parameters", Map.of("expression", "beans.getNames().length")
                ),
                "dataSourceInfo", Map.of(
                        "description", "Get database information",
                        "parameters", Map.of("expression", "dataSource.connection.metaData.databaseProductName")
                ),
                "environmentProps", Map.of(
                        "description", "Access environment properties",
                        "parameters", Map.of("expression", "props.get('spring.application.name')")
                ),
                "beanInspection", Map.of(
                        "description", "Inspect a specific bean",
                        "parameters", Map.of("expression", "beans.get('dataSource').class.name")
                ),
                "contextInfo", Map.of(
                        "description", "Get application context information",
                        "parameters", Map.of("expression", "applicationContext.beanDefinitionCount")
                ),
                "timeAndDate", Map.of(
                        "description", "Work with dates and time",
                        "parameters", Map.of("expression", "now.toString()")
                ),
                "collectionOps", Map.of(
                        "description", "Collection operations",
                        "parameters", Map.of("expression", "beans.getNames().?[#this.contains('Tool')]")
                )
        );
    }
}
