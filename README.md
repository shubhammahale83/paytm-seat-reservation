# Paytm Seat Reservation Backend Service

A high-performance Spring Boot 3.4 REST backend for seat reservation, built with Java 21, Spring Security with JWT authentication, Spring JDBC (`JdbcTemplate`), PostgreSQL, Flyway migrations, Spring Boot Actuator, Micrometer Prometheus metrics, Bean Validation, and Docker containerization.

Designed to handle extreme concurrency stampedes (hot-seat races, multi-seat reservation, user limits, idempotency) with zero double-selling and zero 5xx server errors.

---

## Key Features & Guarantees

1. **Race-Free Hot-Seat Reservation**: Powered by PostgreSQL `SELECT FOR UPDATE` row locks with deterministic alphabetical seat lock ordering to prevent deadlocks.
2. **Strict Per-User Limit (Default 4)**: Enforced atomically via `show_user_counters` table serialization locks.
3. **Idempotency Enforcement**: Guarantees exactly-once processing using `idempotency_records` with composite unique constraints. Same key returns cached payload; same key with different seat body returns 409 Conflict.
4. **All-or-Nothing Partial Seat Requests**: If any seat in a multi-seat request is unavailable, the entire transaction rolls back cleanly with 409 Conflict.
5. **Reconciliation Invariant**: `available_seats + held_seats + confirmed_seats == total_seats` holds at all times.
6. **Observability**: Prometheus metrics at `/actuator/prometheus`, liveness at `/livez`, database-backed readiness at `/readyz`, and structured JSON logging with request correlation IDs.

---

## API Endpoints Summary

| Method | Endpoint | Auth Required | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/shows` | Admin JWT | Create a new show with seat inventory |
| `GET` | `/shows/{id}` | Public / User | Get show status, counts, and per-seat state |
| `POST` | `/shows/{id}/reserve` | User JWT | Reserve seat(s) atomically with idempotency key |
| `POST` | `/reservations/{id}/cancel` | User JWT (Owner) | Cancel a confirmed reservation |
| `GET` | `/livez` | Public | Liveness probe (JVM status) |
| `GET` | `/readyz` | Public | Readiness probe (Database health check) |
| `GET` | `/actuator/prometheus` | Public | Prometheus format metrics |

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

## Quick Start & Local Execution

### Prerequisites
- JDK 21
- Maven 3.9+
- Python 3.9+ (for running the burst script)
- Docker & Docker Compose (optional)

### Build & Run Tests
```bash
# Run unit and integration tests
mvn test

# Package application into executable JAR
mvn clean package
```

### Run with Docker Compose
```bash
docker-compose up --build
```

---

## One-Command Burst Script

The repository includes a one-command burst testing script that reproduces an on-sale stampede against any deployed URL (hot-seat storm, per-user limit concurrency, idempotency retries, key reuse conflicts, and seat reconciliation check).

### Execution Command:
```bash
# Grant execution permissions (Linux/macOS)
chmod +x burst.sh

# Run against local instance or live URL
./burst.sh http://localhost:8080

# Or run directly via Python
python3 scripts/burst.py http://localhost:8080
```

---

## Detailed Write-Up & Architecture Document

See [`WRITEUP.md`](file:///c:/Users/ABC/Documents/paytm-seat-reservation/WRITEUP.md) for complete details on:
- Atomic decision mechanisms & race-free row locking
- Multi-seat lock sorting & deadlock prevention
- Idempotency storage & exactly-once handling
- Explicit cancellation & seat resurrection prevention
- CAP theorem partition trade-offs (CP model choice)
- Alerting & Observability (2 AM page triggers)
- Honest AI usage breakdown
