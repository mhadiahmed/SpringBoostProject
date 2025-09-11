# Multi-stage Docker build for Spring Boost
FROM eclipse-temurin:17-jdk-alpine AS builder

# Set working directory
WORKDIR /app

# Copy build files
COPY mvnw* ./
COPY .mvn .mvn
COPY pom.xml ./

# Download dependencies (this step is cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# Production stage
FROM eclipse-temurin:17-jre-alpine AS production

# Install necessary packages
RUN apk add --no-cache \
    curl \
    ca-certificates \
    && rm -rf /var/cache/apk/*

# Create a non-root user
RUN addgroup -g 1000 springboost && \
    adduser -D -s /bin/sh -u 1000 -G springboost springboost

# Set working directory
WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder /app/target/spring-boost-*.jar app.jar

# Create necessary directories
RUN mkdir -p /app/logs /app/config /app/data && \
    chown -R springboost:springboost /app

# Switch to non-root user
USER springboost

# Expose ports
EXPOSE 8080 28080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set JVM options for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Spring Boot configuration
ENV SPRING_PROFILES_ACTIVE=production
ENV SPRING_BOOST_MCP_ENABLED=true
ENV SPRING_BOOST_MCP_PORT=28080
ENV SPRING_BOOST_MCP_HOST=0.0.0.0

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Labels for metadata
LABEL org.opencontainers.image.title="Spring Boost"
LABEL org.opencontainers.image.description="MCP Server for AI-Assisted Spring Boot Development"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.vendor="Spring Boost Team"
LABEL org.opencontainers.image.url="https://github.com/mhadiahmed/SpringBoostProject"
LABEL org.opencontainers.image.source="https://github.com/mhadiahmed/SpringBoostProject"
LABEL org.opencontainers.image.licenses="MIT"
