# Multi-stage Docker build for Spring Boost
# No Maven wrapper is committed to this repo, so the builder stage uses an
# image with Maven preinstalled instead of ./mvnw.
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy build files
COPY pom.xml ./

# Download dependencies (this step is cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code and the AI guidelines/skills bundled into the jar
COPY src ./src
COPY .ai ./.ai

# Build the application. Skip sources/javadoc jars (only needed for Maven
# Central publishing). target/ now has two jars: the plain library jar and
# the executable spring-boost-*-exec.jar (see pom.xml's <classifier> comment).
RUN mvn clean package -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true

# Production stage
# eclipse-temurin's *-alpine JRE tags don't publish arm64 manifests, so this
# uses the Debian-based tag for compatibility with Apple Silicon / ARM hosts.
FROM eclipse-temurin:17-jre AS production

# Install necessary packages
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user (no explicit uid/gid: the Ubuntu base image
# already reserves 1000 for its default user)
RUN groupadd springboost && \
    useradd -g springboost -s /bin/sh -M springboost

# Set working directory
WORKDIR /app

# Copy the built executable jar from builder stage (the classifier-less jar
# in target/ is the plain library artifact, not runnable — see pom.xml)
COPY --from=builder /app/target/spring-boost-*-exec.jar app.jar

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

# Spring Boot configuration. No SPRING_PROFILES_ACTIVE default: the
# "production" profile requires a real PostgreSQL server, which isn't bundled
# here, so `docker run` out of the box falls back to the base config's H2
# in-memory database. Set SPRING_PROFILES_ACTIVE=production plus DATABASE_URL
# etc. when you have real infra.
ENV SPRING_BOOST_MCP_ENABLED=true
ENV SPRING_BOOST_MCP_PORT=28080
ENV SPRING_BOOST_MCP_HOST=0.0.0.0

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Labels for metadata
LABEL org.opencontainers.image.title="Spring Boost"
LABEL org.opencontainers.image.description="MCP Server for AI-Assisted Spring Boot Development"
LABEL org.opencontainers.image.version="0.1.0"
LABEL org.opencontainers.image.url="https://github.com/mhadiahmed/SpringBoostProject"
LABEL org.opencontainers.image.source="https://github.com/mhadiahmed/SpringBoostProject"
LABEL org.opencontainers.image.licenses="MIT"
