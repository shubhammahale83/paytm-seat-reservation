# Paytm Seat Reservation Backend Service

A high-performance Spring Boot 3.4 REST backend for seat reservation, built with Java 21, Spring Security with JWT authentication, Spring JDBC (JdbcTemplate), PostgreSQL, Flyway migrations, Spring Boot Actuator, Micrometer Prometheus, Bean Validation, and Docker support.

## Architecture & Layered Package Structure

- **`controller`**: REST controllers handling incoming HTTP requests and responses.
- **`service`**: Service layer defining business interfaces and logic placeholders.
- **`repository`**: Data access layer built with Spring `JdbcTemplate` for native SQL operations.
- **`dto`**: Data Transfer Objects with Bean Validation (`@Valid`, `@NotBlank`, `@Size`, etc.).
- **`exception`**: Centralized exception handling via `@RestControllerAdvice` and domain exceptions.
- **`security`**: Spring Security 6 integration with JWT filters, entry point, and user details.
- **`config`**: Spring configuration beans (`JdbcConfig`, `SecurityConfig`, `ActuatorConfig`).
- **`logging`**: Dynamic AOP method tracing aspect (`LoggingAspect`) and HTTP request logging filter.

---

## Configuration & Environment Variables

All database and security credentials are supplied via environment variables (no hardcoded secrets):

| Variable | Description | Default Value |
| :--- | :--- | :--- |
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/paytm_seat_reservation` |
| `DB_USERNAME` | PostgreSQL database user | `postgres` |
| `DB_PASSWORD` | PostgreSQL database password | `postgres` |
| `FLYWAY_ENABLED` | Toggle Flyway schema migrations | `true` |
| `JWT_SECRET` | HMAC Secret key for signing JWTs | Base64 / Hex Secret String |
| `JWT_EXPIRATION_MS` | JWT validity token duration in ms | `86400000` (24h) |

---

## Build & Run

### Prerequisites
- JDK 21
- Maven 3.9+
- Docker & Docker Compose (optional)

### Build & Run Tests
```bash
# Run unit and integration tests (uses isolated H2 test profile)
mvn test

# Package application into executable JAR
mvn clean package
```

### Run Locally
```bash
# Set environment variables (optional overrides)
export DB_URL=jdbc:postgresql://localhost:5432/paytm_seat_reservation
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=YourSuperSecretKeyGoesHereMustBeLongEnoughForHS256

# Start application
java -jar target/paytm-seat-reservation-0.0.1-SNAPSHOT.jar
```

### Run with Docker Compose
```bash
docker-compose up --build
```

---

## Observability & Metrics

- **Health Check**: `http://localhost:8080/actuator/health`
- **Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`
- **Actuator Info**: `http://localhost:8080/actuator/info`
