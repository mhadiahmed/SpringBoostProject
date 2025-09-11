package com.springboost.mcp.tools.impl;

import com.springboost.mcp.tools.McpTool;
import com.springboost.mcp.tools.McpToolException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tool for running and analyzing Spring Boot tests
 * Provides test execution, result parsing, and coverage information
 */
@Slf4j
@Component
public class TestExecutionTool implements McpTool {
    
    private final ApplicationContext applicationContext;
    private final Environment environment;
    
    // Test result patterns
    private static final Pattern TEST_RESULT_PATTERN = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)"
    );
    
    private static final Pattern TEST_CLASS_PATTERN = Pattern.compile(
            "Running (.+)$"
    );
    
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile(
            "(.+?)\\s+Time elapsed:\\s+(\\d+\\.\\d+)\\s+s\\s+<<<\\s+(FAILURE|ERROR)?!"
    );
    
    private static final Pattern MAVEN_PROJECT_PATTERN = Pattern.compile(
            "Building (.+?) (.+)"
    );
    
    @Autowired
    public TestExecutionTool(ApplicationContext applicationContext, Environment environment) {
        this.applicationContext = applicationContext;
        this.environment = environment;
    }
    
    @Override
    public String getName() {
        return "test-execution";
    }
    
    @Override
    public String getDescription() {
        return "Run and analyze Spring Boot tests with detailed results and coverage information";
    }
    
    @Override
    public String getCategory() {
        return "testing";
    }
    
    @Override
    public boolean requiresElevatedPrivileges() {
        return true; // Test execution may require system access
    }
    
    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "testClass", Map.of(
                        "type", "string",
                        "description", "Specific test class to run (e.g., 'SpringBoostApplicationTests'). If not provided, runs all tests"
                ),
                "testMethod", Map.of(
                        "type", "string",
                        "description", "Specific test method to run (requires testClass to be specified)"
                ),
                "profile", Map.of(
                        "type", "string",
                        "description", "Spring profile to use for tests",
                        "default", "test"
                ),
                "timeout", Map.of(
                        "type", "integer",
                        "description", "Test execution timeout in seconds",
                        "default", 300,
                        "minimum", 30,
                        "maximum", 1800
                ),
                "includeCoverage", Map.of(
                        "type", "boolean",
                        "description", "Include test coverage information",
                        "default", false
                ),
                "verbose", Map.of(
                        "type", "boolean",
                        "description", "Include verbose test output",
                        "default", false
                ),
                "skipIntegrationTests", Map.of(
                        "type", "boolean",
                        "description", "Skip integration tests (tests ending with IT)",
                        "default", false
                ),
                "outputFormat", Map.of(
                        "type", "string",
                        "description", "Output format for test results",
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
            String testClass = (String) params.get("testClass");
            String testMethod = (String) params.get("testMethod");
            String profile = (String) params.getOrDefault("profile", "test");
            int timeout = ((Number) params.getOrDefault("timeout", 300)).intValue();
            boolean includeCoverage = (boolean) params.getOrDefault("includeCoverage", false);
            boolean verbose = (boolean) params.getOrDefault("verbose", false);
            boolean skipIntegrationTests = (boolean) params.getOrDefault("skipIntegrationTests", false);
            String outputFormat = (String) params.getOrDefault("outputFormat", "detailed");
            
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", System.currentTimeMillis());
            result.put("testConfiguration", getTestConfiguration(testClass, testMethod, profile));
            
            // Detect project type and build tool
            ProjectInfo projectInfo = detectProjectInfo();
            result.put("projectInfo", projectInfo.toMap());
            
            // Build test command
            List<String> command = buildTestCommand(projectInfo, testClass, testMethod, profile, 
                    includeCoverage, skipIntegrationTests);
            result.put("command", String.join(" ", command));
            
            // Execute tests
            TestExecutionResult executionResult = executeTests(command, timeout, verbose);
            result.put("execution", executionResult.toMap());
            
            // Parse test results
            TestResults testResults = parseTestResults(executionResult.getOutput());
            result.put("results", formatTestResults(testResults, outputFormat));
            
            // Include coverage if requested
            if (includeCoverage && testResults.isSuccessful()) {
                result.put("coverage", getCoverageInformation(projectInfo));
            }
            
            // Add suggestions for failed tests
            if (!testResults.isSuccessful()) {
                result.put("suggestions", generateFailureSuggestions(testResults, executionResult));
            }
            
            return result;
            
        } catch (Exception e) {
            if (e instanceof McpToolException) {
                throw (McpToolException) e;
            }
            log.error("Failed to execute tests: {}", e.getMessage(), e);
            throw new McpToolException(getName(), "Failed to execute tests: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> getTestConfiguration(String testClass, String testMethod, String profile) {
        Map<String, Object> config = new HashMap<>();
        config.put("testClass", testClass);
        config.put("testMethod", testMethod);
        config.put("profile", profile);
        config.put("scope", testMethod != null ? "method" : (testClass != null ? "class" : "all"));
        return config;
    }
    
    private ProjectInfo detectProjectInfo() {
        String projectRoot = System.getProperty("user.dir");
        Path projectPath = Paths.get(projectRoot);
        
        ProjectInfo info = new ProjectInfo();
        info.setRootPath(projectRoot);
        
        // Check for Maven
        if (Files.exists(projectPath.resolve("pom.xml"))) {
            info.setBuildTool("maven");
            info.setTestDirectory("src/test/java");
            info.setTestResourcesDirectory("src/test/resources");
        }
        // Check for Gradle
        else if (Files.exists(projectPath.resolve("build.gradle")) || 
                 Files.exists(projectPath.resolve("build.gradle.kts"))) {
            info.setBuildTool("gradle");
            info.setTestDirectory("src/test/java");
            info.setTestResourcesDirectory("src/test/resources");
        } else {
            throw new RuntimeException("No supported build tool found (Maven or Gradle)");
        }
        
        // Detect available test directories
        info.setTestDirectoriesExist(Files.exists(projectPath.resolve(info.getTestDirectory())));
        
        return info;
    }
    
    private List<String> buildTestCommand(ProjectInfo projectInfo, String testClass, String testMethod,
                                        String profile, boolean includeCoverage, boolean skipIntegrationTests) {
        List<String> command = new ArrayList<>();
        
        if ("maven".equals(projectInfo.getBuildTool())) {
            command.add("mvn");
            command.add("test");
            
            // Add profile
            command.add("-Dspring.profiles.active=" + profile);
            
            // Add specific test class/method
            if (testClass != null) {
                if (testMethod != null) {
                    command.add("-Dtest=" + testClass + "#" + testMethod);
                } else {
                    command.add("-Dtest=" + testClass);
                }
            }
            
            // Skip integration tests
            if (skipIntegrationTests) {
                command.add("-DskipITs=true");
            }
            
            // Add coverage
            if (includeCoverage) {
                command.add("-Dspring-boot.run.profiles=" + profile);
                command.add("-Dmaven.test.failure.ignore=true");
            }
            
        } else if ("gradle".equals(projectInfo.getBuildTool())) {
            command.add("./gradlew");
            command.add("test");
            
            // Add profile
            command.add("-Dspring.profiles.active=" + profile);
            
            // Add specific test class/method
            if (testClass != null) {
                command.add("--tests");
                if (testMethod != null) {
                    command.add(testClass + "." + testMethod);
                } else {
                    command.add(testClass);
                }
            }
            
            // Add coverage
            if (includeCoverage) {
                command.add("jacocoTestReport");
            }
        }
        
        return command;
    }
    
    private TestExecutionResult executeTests(List<String> command, int timeoutSeconds, boolean verbose) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(System.getProperty("user.dir")));
        processBuilder.redirectErrorStream(true);
        
        long startTime = System.currentTimeMillis();
        Process process = processBuilder.start();
        
        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                if (verbose) {
                    log.info("Test output: {}", line);
                }
            }
        }
        
        boolean completed;
        try {
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Test execution was interrupted", e);
        }
        
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("Test execution timed out after " + timeoutSeconds + " seconds");
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        int exitCode = process.exitValue();
        
        return new TestExecutionResult(output, exitCode, executionTime, completed);
    }
    
    private TestResults parseTestResults(List<String> output) {
        TestResults results = new TestResults();
        List<TestResult> testResults = new ArrayList<>();
        
        String currentTestClass = null;
        boolean inTestExecution = false;
        
        for (String line : output) {
            // Parse Maven project info
            Matcher projectMatcher = MAVEN_PROJECT_PATTERN.matcher(line);
            if (projectMatcher.find()) {
                results.setProjectName(projectMatcher.group(1));
                results.setProjectVersion(projectMatcher.group(2));
                continue;
            }
            
            // Parse test class
            Matcher classMatcher = TEST_CLASS_PATTERN.matcher(line);
            if (classMatcher.find()) {
                currentTestClass = classMatcher.group(1);
                inTestExecution = true;
                continue;
            }
            
            // Parse test results summary
            Matcher resultMatcher = TEST_RESULT_PATTERN.matcher(line);
            if (resultMatcher.find()) {
                results.setTotalTests(Integer.parseInt(resultMatcher.group(1)));
                results.setFailures(Integer.parseInt(resultMatcher.group(2)));
                results.setErrors(Integer.parseInt(resultMatcher.group(3)));
                results.setSkipped(Integer.parseInt(resultMatcher.group(4)));
                results.setSuccessful(results.getFailures() == 0 && results.getErrors() == 0);
                continue;
            }
            
            // Parse individual test methods
            Matcher methodMatcher = TEST_METHOD_PATTERN.matcher(line);
            if (methodMatcher.find() && currentTestClass != null) {
                TestResult testResult = new TestResult();
                testResult.setClassName(currentTestClass);
                testResult.setMethodName(methodMatcher.group(1));
                testResult.setExecutionTime(Double.parseDouble(methodMatcher.group(2)));
                testResult.setStatus(methodMatcher.group(3) != null ? methodMatcher.group(3) : "SUCCESS");
                testResults.add(testResult);
                continue;
            }
            
            // Collect error messages
            if (line.contains("ERROR") || line.contains("FAILURE")) {
                results.getErrorMessages().add(line);
            }
        }
        
        results.setTestResults(testResults);
        return results;
    }
    
    private Map<String, Object> formatTestResults(TestResults results, String format) {
        Map<String, Object> formatted = new HashMap<>();
        
        switch (format) {
            case "summary":
                formatted.put("total", results.getTotalTests());
                formatted.put("passed", results.getTotalTests() - results.getFailures() - results.getErrors() - results.getSkipped());
                formatted.put("failed", results.getFailures());
                formatted.put("errors", results.getErrors());
                formatted.put("skipped", results.getSkipped());
                formatted.put("successful", results.isSuccessful());
                break;
                
            case "detailed":
                formatted.put("summary", Map.of(
                        "total", results.getTotalTests(),
                        "passed", results.getTotalTests() - results.getFailures() - results.getErrors() - results.getSkipped(),
                        "failed", results.getFailures(),
                        "errors", results.getErrors(),
                        "skipped", results.getSkipped(),
                        "successful", results.isSuccessful()
                ));
                formatted.put("testClasses", getTestClassSummary(results.getTestResults()));
                if (!results.getErrorMessages().isEmpty()) {
                    formatted.put("errorMessages", results.getErrorMessages());
                }
                break;
                
            case "full":
            default:
                formatted.put("summary", Map.of(
                        "total", results.getTotalTests(),
                        "passed", results.getTotalTests() - results.getFailures() - results.getErrors() - results.getSkipped(),
                        "failed", results.getFailures(),
                        "errors", results.getErrors(),
                        "skipped", results.getSkipped(),
                        "successful", results.isSuccessful()
                ));
                formatted.put("projectInfo", Map.of(
                        "name", results.getProjectName(),
                        "version", results.getProjectVersion()
                ));
                formatted.put("testResults", results.getTestResults().stream()
                        .map(TestResult::toMap)
                        .collect(Collectors.toList()));
                if (!results.getErrorMessages().isEmpty()) {
                    formatted.put("errorMessages", results.getErrorMessages());
                }
                break;
        }
        
        return formatted;
    }
    
    private Map<String, Object> getTestClassSummary(List<TestResult> testResults) {
        return testResults.stream()
                .collect(Collectors.groupingBy(TestResult::getClassName))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Map.of(
                                "total", entry.getValue().size(),
                                "passed", entry.getValue().stream()
                                        .mapToInt(t -> "SUCCESS".equals(t.getStatus()) ? 1 : 0)
                                        .sum(),
                                "failed", entry.getValue().stream()
                                        .mapToInt(t -> "FAILURE".equals(t.getStatus()) || "ERROR".equals(t.getStatus()) ? 1 : 0)
                                        .sum(),
                                "totalTime", entry.getValue().stream()
                                        .mapToDouble(TestResult::getExecutionTime)
                                        .sum()
                        )
                ));
    }
    
    private Map<String, Object> getCoverageInformation(ProjectInfo projectInfo) {
        Map<String, Object> coverage = new HashMap<>();
        
        // Try to find coverage reports
        if ("maven".equals(projectInfo.getBuildTool())) {
            // Look for JaCoCo reports
            Path jacocoPath = Paths.get(projectInfo.getRootPath(), "target", "site", "jacoco", "index.html");
            if (Files.exists(jacocoPath)) {
                coverage.put("jacocoReport", jacocoPath.toString());
                coverage.put("type", "jacoco");
            }
        } else if ("gradle".equals(projectInfo.getBuildTool())) {
            // Look for JaCoCo reports in Gradle structure
            Path jacocoPath = Paths.get(projectInfo.getRootPath(), "build", "reports", "jacoco", "test", "html", "index.html");
            if (Files.exists(jacocoPath)) {
                coverage.put("jacocoReport", jacocoPath.toString());
                coverage.put("type", "jacoco");
            }
        }
        
        if (coverage.isEmpty()) {
            coverage.put("available", false);
            coverage.put("message", "No coverage reports found. Run tests with coverage enabled.");
        } else {
            coverage.put("available", true);
        }
        
        return coverage;
    }
    
    private List<String> generateFailureSuggestions(TestResults results, TestExecutionResult executionResult) {
        List<String> suggestions = new ArrayList<>();
        
        if (results.getErrors() > 0) {
            suggestions.add("Check for configuration issues or missing dependencies");
            suggestions.add("Verify that test database and required services are available");
            suggestions.add("Check application context loading in test environment");
        }
        
        if (results.getFailures() > 0) {
            suggestions.add("Review test assertions and expected vs actual values");
            suggestions.add("Check if test data setup is correct");
            suggestions.add("Verify that mock configurations are appropriate");
        }
        
        if (executionResult.getExitCode() != 0) {
            suggestions.add("Check build tool configuration and dependencies");
            suggestions.add("Ensure test profile configuration is correct");
        }
        
        // Check for common error patterns in output
        for (String line : executionResult.getOutput()) {
            if (line.contains("ApplicationContextException")) {
                suggestions.add("Application context failed to load - check @SpringBootTest configuration");
            }
            if (line.contains("NoSuchBeanDefinitionException")) {
                suggestions.add("Missing bean definition - check @MockBean or @TestConfiguration");
            }
            if (line.contains("DataIntegrityViolationException")) {
                suggestions.add("Database constraint violation - check test data setup");
            }
        }
        
        return suggestions.stream().distinct().limit(5).collect(Collectors.toList());
    }
    
    // Data classes
    private static class ProjectInfo {
        private String rootPath;
        private String buildTool;
        private String testDirectory;
        private String testResourcesDirectory;
        private boolean testDirectoriesExist;
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("rootPath", rootPath);
            map.put("buildTool", buildTool);
            map.put("testDirectory", testDirectory);
            map.put("testResourcesDirectory", testResourcesDirectory);
            map.put("testDirectoriesExist", testDirectoriesExist);
            return map;
        }
        
        // Getters and setters
        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }
        public String getBuildTool() { return buildTool; }
        public void setBuildTool(String buildTool) { this.buildTool = buildTool; }
        public String getTestDirectory() { return testDirectory; }
        public void setTestDirectory(String testDirectory) { this.testDirectory = testDirectory; }
        public String getTestResourcesDirectory() { return testResourcesDirectory; }
        public void setTestResourcesDirectory(String testResourcesDirectory) { this.testResourcesDirectory = testResourcesDirectory; }
        public boolean isTestDirectoriesExist() { return testDirectoriesExist; }
        public void setTestDirectoriesExist(boolean testDirectoriesExist) { this.testDirectoriesExist = testDirectoriesExist; }
    }
    
    private static class TestExecutionResult {
        private final List<String> output;
        private final int exitCode;
        private final long executionTimeMs;
        private final boolean completed;
        
        public TestExecutionResult(List<String> output, int exitCode, long executionTimeMs, boolean completed) {
            this.output = output;
            this.exitCode = exitCode;
            this.executionTimeMs = executionTimeMs;
            this.completed = completed;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("exitCode", exitCode);
            map.put("executionTimeMs", executionTimeMs);
            map.put("completed", completed);
            map.put("outputLines", output.size());
            return map;
        }
        
        public List<String> getOutput() { return output; }
        public int getExitCode() { return exitCode; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public boolean isCompleted() { return completed; }
    }
    
    private static class TestResults {
        private String projectName;
        private String projectVersion;
        private int totalTests;
        private int failures;
        private int errors;
        private int skipped;
        private boolean successful;
        private List<TestResult> testResults = new ArrayList<>();
        private List<String> errorMessages = new ArrayList<>();
        
        // Getters and setters
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getProjectVersion() { return projectVersion; }
        public void setProjectVersion(String projectVersion) { this.projectVersion = projectVersion; }
        public int getTotalTests() { return totalTests; }
        public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
        public int getFailures() { return failures; }
        public void setFailures(int failures) { this.failures = failures; }
        public int getErrors() { return errors; }
        public void setErrors(int errors) { this.errors = errors; }
        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        public boolean isSuccessful() { return successful; }
        public void setSuccessful(boolean successful) { this.successful = successful; }
        public List<TestResult> getTestResults() { return testResults; }
        public void setTestResults(List<TestResult> testResults) { this.testResults = testResults; }
        public List<String> getErrorMessages() { return errorMessages; }
        public void setErrorMessages(List<String> errorMessages) { this.errorMessages = errorMessages; }
    }
    
    private static class TestResult {
        private String className;
        private String methodName;
        private double executionTime;
        private String status;
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("className", className);
            map.put("methodName", methodName);
            map.put("executionTime", executionTime);
            map.put("status", status);
            return map;
        }
        
        // Getters and setters
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public double getExecutionTime() { return executionTime; }
        public void setExecutionTime(double executionTime) { this.executionTime = executionTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    @Override
    public Map<String, Object> getUsageExamples() {
        return Map.of(
                "runAllTests", Map.of(
                        "description", "Run all tests with basic reporting",
                        "parameters", Map.of()
                ),
                "runSpecificClass", Map.of(
                        "description", "Run tests for a specific class",
                        "parameters", Map.of("testClass", "SpringBoostApplicationTests")
                ),
                "runSpecificMethod", Map.of(
                        "description", "Run a specific test method",
                        "parameters", Map.of(
                                "testClass", "SpringBoostApplicationTests",
                                "testMethod", "contextLoads"
                        )
                ),
                "runWithCoverage", Map.of(
                        "description", "Run tests with coverage information",
                        "parameters", Map.of(
                                "includeCoverage", true,
                                "outputFormat", "full"
                        )
                ),
                "runWithProfile", Map.of(
                        "description", "Run tests with specific profile",
                        "parameters", Map.of(
                                "profile", "integration",
                                "timeout", 600
                        )
                )
        );
    }
}
