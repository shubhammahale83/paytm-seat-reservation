# Stage 1: Build stage with Maven and JDK 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy dependency definition and source code
COPY pom.xml .
COPY src ./src

# Package application into an executable JAR (skip unit tests for Docker build efficiency)
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage with Java 21 JRE
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy executable jar from builder stage
COPY --from=builder /build/target/paytm-seat-reservation-*.jar app.jar

# Set correct ownership for application directory
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Default port configuration (overridable via environment variable)
ENV PORT=8080 \
    JAVA_OPTS=""

# Expose default application port
EXPOSE 8080

# Container healthcheck using readiness probe
HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT}/readyz || exit 1

# Clean container startup respecting PORT environment variable
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]
