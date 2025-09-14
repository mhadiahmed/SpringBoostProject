# Spring Boot Docker Integration Guidelines

## Overview

Guidelines for containerizing Spring Boot applications with Docker, including best practices for multi-stage builds, security, performance optimization, and production deployment patterns.

## Basic Dockerfile Patterns

### Standard Spring Boot Dockerfile

```dockerfile
# Multi-stage build for Spring Boot application
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw clean package -DskipTests -B

# Production stage
FROM eclipse-temurin:17-jre-alpine AS production

# Add non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership of the app directory
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# JVM tuning and application startup
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+PrintGCDetails", \
    "-XX:+PrintGCTimeStamps", \
    "-jar", \
    "app.jar"]
```

### Optimized Production Dockerfile

```dockerfile
# Build stage with Maven cache optimization
FROM eclipse-temurin:17-jdk-alpine AS builder

# Install dependencies for native compilation (if needed)
RUN apk add --no-cache build-base zlib-dev

WORKDIR /app

# Copy dependency files first
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Cache dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B && \
    mkdir -p target/dependency && \
    (cd target/dependency; jar -xf ../*.jar)

# Production stage with layered approach
FROM eclipse-temurin:17-jre-alpine AS production

# Security updates and utilities
RUN apk update && \
    apk upgrade && \
    apk add --no-cache curl wget && \
    rm -rf /var/cache/apk/*

# Create application user
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

WORKDIR /app

# Copy application layers for better caching
COPY --from=builder --chown=spring:spring /app/target/dependency/BOOT-INF/lib /app/lib
COPY --from=builder --chown=spring:spring /app/target/dependency/META-INF /app/META-INF
COPY --from=builder --chown=spring:spring /app/target/dependency/BOOT-INF/classes /app

# Switch to application user
USER spring

# Expose port
EXPOSE 8080

# Health check with application-specific endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# Optimized JVM startup
ENTRYPOINT ["java", \
    "-cp", "/app:/app/lib/*", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:+UseStringDeduplication", \
    "-Dspring.profiles.active=docker", \
    "com.springboost.SpringBoostApplication"]
```

### Gradle-based Dockerfile

```dockerfile
# Gradle multi-stage build
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# Download dependencies
RUN ./gradlew dependencies --no-daemon

# Copy source and build
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

# Production stage
FROM eclipse-temurin:17-jre-alpine AS production

RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /app/build/libs/*.jar app.jar

USER spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", \
    "app.jar"]
```

## Docker Compose for Development

### Basic Development Setup

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
      target: production
    container_name: spring-boost-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker,dev
      - DATABASE_URL=jdbc:postgresql://db:5432/springboost
      - DATABASE_USERNAME=springboost
      - DATABASE_PASSWORD=password
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - spring-boost-network
    volumes:
      - app-logs:/app/logs
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  db:
    image: postgres:15-alpine
    container_name: spring-boost-db
    environment:
      - POSTGRES_DB=springboost
      - POSTGRES_USER=springboost
      - POSTGRES_PASSWORD=password
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro
    networks:
      - spring-boost-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U springboost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: spring-boost-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - spring-boost-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3

  nginx:
    image: nginx:alpine
    container_name: spring-boost-nginx
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
    depends_on:
      - app
    networks:
      - spring-boost-network
    restart: unless-stopped

volumes:
  postgres-data:
    driver: local
  redis-data:
    driver: local
  app-logs:
    driver: local

networks:
  spring-boost-network:
    driver: bridge
```

### Production-ready Docker Compose

```yaml
# docker-compose.prod.yml
version: '3.8'

services:
  app:
    image: ${REGISTRY_URL}/spring-boost:${VERSION}
    container_name: spring-boost-app-${ENVIRONMENT}
    ports:
      - "${APP_PORT}:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker,${ENVIRONMENT}
      - DATABASE_URL=${DATABASE_URL}
      - DATABASE_USERNAME=${DATABASE_USERNAME}
      - DATABASE_PASSWORD=${DATABASE_PASSWORD}
      - REDIS_HOST=${REDIS_HOST}
      - REDIS_PORT=${REDIS_PORT}
      - JVM_OPTS=${JVM_OPTS}
      - NEW_RELIC_LICENSE_KEY=${NEW_RELIC_LICENSE_KEY}
      - NEW_RELIC_APP_NAME=spring-boost-${ENVIRONMENT}
    secrets:
      - db_password
      - jwt_secret
      - api_keys
    deploy:
      replicas: ${REPLICAS:-2}
      resources:
        limits:
          memory: 1G
          cpus: '1.0'
        reservations:
          memory: 512M
          cpus: '0.5'
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
        window: 120s
      update_config:
        parallelism: 1
        delay: 10s
        failure_action: rollback
        order: start-first
    networks:
      - spring-boost-network
      - monitoring-network
    volumes:
      - app-logs:/app/logs
    logging:
      driver: json-file
      options:
        max-size: "100m"
        max-file: "5"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health/readiness"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s

  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/usr/share/prometheus/console_libraries'
      - '--web.console.templates=/usr/share/prometheus/consoles'
      - '--web.enable-lifecycle'
    networks:
      - monitoring-network

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
    volumes:
      - grafana-data:/var/lib/grafana
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
    networks:
      - monitoring-network

secrets:
  db_password:
    external: true
  jwt_secret:
    external: true
  api_keys:
    external: true

volumes:
  prometheus-data:
    driver: local
  grafana-data:
    driver: local
  app-logs:
    driver: local

networks:
  spring-boost-network:
    driver: overlay
    attachable: true
  monitoring-network:
    driver: overlay
    attachable: true
```

## Application Configuration for Docker

### Docker-specific Configuration

```yaml
# application-docker.yml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:h2:mem:testdb}
    username: ${DATABASE_USERNAME:sa}
    password: ${DATABASE_PASSWORD:}
    driver-class-name: ${DATABASE_DRIVER:org.h2.Driver}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:20}
      minimum-idle: ${DB_POOL_MIN_IDLE:5}
      connection-timeout: ${DB_CONNECTION_TIMEOUT:30000}
      idle-timeout: ${DB_IDLE_TIMEOUT:600000}
      max-lifetime: ${DB_MAX_LIFETIME:1800000}

  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: ${REDIS_TIMEOUT:2000}
    lettuce:
      pool:
        max-active: ${REDIS_POOL_MAX_ACTIVE:10}
        max-idle: ${REDIS_POOL_MAX_IDLE:10}
        min-idle: ${REDIS_POOL_MIN_IDLE:1}

  jpa:
    hibernate:
      ddl-auto: ${JPA_DDL_AUTO:validate}
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        use_sql_comments: false
        jdbc:
          batch_size: 25
          order_inserts: true
          order_updates: true

server:
  port: 8080
  shutdown: graceful
  tomcat:
    threads:
      max: ${SERVER_TOMCAT_MAX_THREADS:200}
      min-spare: ${SERVER_TOMCAT_MIN_SPARE:10}
    connection-timeout: ${SERVER_CONNECTION_TIMEOUT:20000}
    accept-count: ${SERVER_ACCEPT_COUNT:100}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    com.springboost: ${LOG_LEVEL_APP:INFO}
    org.springframework: ${LOG_LEVEL_SPRING:INFO}
    org.hibernate: ${LOG_LEVEL_HIBERNATE:WARN}
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level [%X{traceId:-},%X{spanId:-}] %logger{36} - %msg%n"
    file: "%d{ISO8601} [%thread] %-5level [%X{traceId:-},%X{spanId:-}] %logger{36} - %msg%n"
  file:
    path: ${LOG_FILE_PATH:/app/logs}
    name: ${LOG_FILE_NAME:spring-boost.log}
```

### Environment-specific Configurations

```bash
# .env.development
SPRING_PROFILES_ACTIVE=docker,dev
DATABASE_URL=jdbc:postgresql://db:5432/springboost_dev
DATABASE_USERNAME=springboost
DATABASE_PASSWORD=dev_password
REDIS_HOST=redis
REDIS_PORT=6379
LOG_LEVEL_APP=DEBUG
JPA_DDL_AUTO=update

# .env.production
SPRING_PROFILES_ACTIVE=docker,prod
DATABASE_URL=jdbc:postgresql://prod-db:5432/springboost_prod
DATABASE_USERNAME=springboost
DATABASE_PASSWORD=${DB_PASSWORD}
REDIS_HOST=prod-redis
REDIS_PORT=6379
LOG_LEVEL_APP=INFO
JPA_DDL_AUTO=validate
JVM_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC
```

## Docker Build and Deployment Scripts

### Build Script

```bash
#!/bin/bash
# scripts/docker-build.sh

set -e

# Configuration
IMAGE_NAME="spring-boost"
REGISTRY_URL="${REGISTRY_URL:-localhost:5000}"
VERSION="${VERSION:-latest}"
ENVIRONMENT="${ENVIRONMENT:-dev}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Building Spring Boost Docker image...${NC}"
echo "Image: ${REGISTRY_URL}/${IMAGE_NAME}:${VERSION}"
echo "Environment: ${ENVIRONMENT}"

# Build multi-stage Docker image
docker build \
  --target production \
  --build-arg VERSION=${VERSION} \
  --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
  --build-arg VCS_REF=$(git rev-parse --short HEAD) \
  -t ${REGISTRY_URL}/${IMAGE_NAME}:${VERSION} \
  -t ${REGISTRY_URL}/${IMAGE_NAME}:latest \
  .

echo -e "${GREEN}Build completed successfully!${NC}"

# Security scan (if trivy is available)
if command -v trivy &> /dev/null; then
    echo -e "${YELLOW}Running security scan...${NC}"
    trivy image --severity HIGH,CRITICAL ${REGISTRY_URL}/${IMAGE_NAME}:${VERSION}
fi

# Push to registry if specified
if [ "${PUSH_IMAGE}" = "true" ]; then
    echo -e "${YELLOW}Pushing image to registry...${NC}"
    docker push ${REGISTRY_URL}/${IMAGE_NAME}:${VERSION}
    docker push ${REGISTRY_URL}/${IMAGE_NAME}:latest
    echo -e "${GREEN}Image pushed successfully!${NC}"
fi

echo -e "${GREEN}Docker build process completed!${NC}"
```

### Deployment Script

```bash
#!/bin/bash
# scripts/docker-deploy.sh

set -e

ENVIRONMENT="${1:-dev}"
COMPOSE_FILE="docker-compose.yml"

if [ "$ENVIRONMENT" = "prod" ]; then
    COMPOSE_FILE="docker-compose.prod.yml"
fi

echo "Deploying Spring Boost application..."
echo "Environment: $ENVIRONMENT"
echo "Compose file: $COMPOSE_FILE"

# Load environment variables
if [ -f ".env.${ENVIRONMENT}" ]; then
    export $(cat .env.${ENVIRONMENT} | xargs)
fi

# Pull latest images
docker-compose -f $COMPOSE_FILE pull

# Deploy with zero-downtime
docker-compose -f $COMPOSE_FILE up -d --remove-orphans

# Wait for health check
echo "Waiting for application to be healthy..."
timeout 120 bash -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'

echo "Deployment completed successfully!"

# Cleanup old images
docker image prune -f
```

## Nginx Configuration

```nginx
# nginx/nginx.conf
events {
    worker_connections 1024;
}

http {
    upstream spring_boot_app {
        server app:8080 max_fails=3 fail_timeout=30s;
        keepalive 32;
    }

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;
    
    # Connection limiting
    limit_conn_zone $binary_remote_addr zone=addr:10m;

    server {
        listen 80;
        server_name localhost;

        # Security headers
        add_header X-Frame-Options "SAMEORIGIN" always;
        add_header X-XSS-Protection "1; mode=block" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "no-referrer-when-downgrade" always;
        add_header Content-Security-Policy "default-src 'self' http: https: data: blob: 'unsafe-inline'" always;

        # Gzip compression
        gzip on;
        gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

        # Rate limiting
        limit_req zone=api burst=20 nodelay;
        limit_conn addr 10;

        location / {
            proxy_pass http://spring_boot_app;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # Timeouts
            proxy_connect_timeout 30s;
            proxy_send_timeout 30s;
            proxy_read_timeout 30s;
            
            # Buffering
            proxy_buffering on;
            proxy_buffer_size 4k;
            proxy_buffers 8 4k;
        }

        location /actuator/health {
            proxy_pass http://spring_boot_app;
            access_log off;
        }

        # Static content caching
        location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
            proxy_pass http://spring_boot_app;
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }
}
```

## Security Best Practices

### Dockerfile Security

```dockerfile
# Secure Dockerfile example
FROM eclipse-temurin:17-jre-alpine AS production

# Update packages and remove cache
RUN apk update && \
    apk upgrade && \
    apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

# Create non-root user with specific UID/GID
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

# Set up application directory with proper permissions
WORKDIR /app
RUN chown appuser:appgroup /app

# Copy application with proper ownership
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Remove unnecessary packages
RUN apk del wget

# Switch to non-root user
USER appuser

# Use non-root port
EXPOSE 8080

# Secure JVM options
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dfile.encoding=UTF-8", \
    "-Duser.timezone=UTC", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.awt.headless=true", \
    "-jar", \
    "app.jar"]
```

### Docker Compose Security

```yaml
# Secure docker-compose.yml
version: '3.8'

services:
  app:
    image: spring-boost:latest
    read_only: true
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=1g
    volumes:
      - app-logs:/app/logs:rw
    environment:
      - SPRING_PROFILES_ACTIVE=docker,prod
    secrets:
      - source: db_password
        target: /run/secrets/db_password
        uid: '10001'
        gid: '10001'
        mode: 0400
```

This comprehensive Docker integration guide provides production-ready patterns for containerizing Spring Boot applications with security, performance, and maintainability in mind.
