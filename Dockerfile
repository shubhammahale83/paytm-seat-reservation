# Multi-stage Dockerfile for Spring Boot paytm-seat-reservation
# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw package -DskipTests || mvn package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/paytm-seat-reservation-*.jar app.jar

ENV PORT=8080 \
    DB_URL=jdbc:postgresql://postgres-db:5432/paytm_seat_reservation \
    DB_USERNAME=postgres \
    DB_PASSWORD=postgres \
    JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970 \
    JWT_EXPIRATION_MS=86400000

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
