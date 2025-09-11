package com.springboost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boost Application - MCP Server for AI-assisted Spring Boot development
 * 
 * This application provides a Model Context Protocol (MCP) server that enables
 * AI coding assistants to interact with Spring Boot applications through
 * specialized tools and context-aware documentation.
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties
@ComponentScan(basePackages = "com.springboost")
public class SpringBoostApplication {

    public static void main(String[] args) {
        System.setProperty("spring.application.name", "spring-boost");
        
        // Banner customization
        System.setProperty("spring.banner.location", "classpath:banner.txt");
        
        log.info("Starting Spring Boost MCP Server...");
        
        SpringApplication app = new SpringApplication(SpringBoostApplication.class);
        
        // Configure application properties
        app.setAdditionalProfiles("mcp-server");
        
        try {
            var context = app.run(args);
            log.info("Spring Boost MCP Server started successfully!");
            log.info("Available tools: {}", context.getBeansOfType(com.springboost.mcp.tools.McpTool.class).size());
        } catch (Exception e) {
            log.error("Failed to start Spring Boost MCP Server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
