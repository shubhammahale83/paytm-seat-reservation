# Paytm Seat Reservation System — Architectural & Technical Write-up

## 1. The Atomic Decision & Concurrency Architecture

### Core Atomic Mechanism
To guarantee absolute correctness under extreme concurrency (where thousands of buyers stampede the same hot seats at on-sale time), the system enforces atomic reservation decisions at the relational database level using **PostgreSQL Row-Level Pessimistic Locking (`SELECT ... FOR UPDATE`)**.

Read-then-write patterns (`if seat.is_free(): seat.book()`) fail catastrophically under load due to race conditions. Instead, our design serializes all seat reservation attempts at the database engine level.

### Multi-Seat Operations & Deadlock Prevention
When a user requests multiple seats (e.g., `["A15", "A12", "A14"]`), deadlocks can occur if concurrent transactions lock seats in different orders (e.g., Tx 1 locks `A12` then `A15`, while Tx 2 locks `A15` then `A12`).

To make multi-seat reservations strictly race-free and deadlock-free:
1. **Deterministic Alphabetical Lock Ordering**: Requested seat labels are normalized, trimmed, deduplicated, and **sorted lexicographically** prior to acquiring row locks.
2. **Strict Lock Hierarchy**:
   - `Idempotency Record` lock (`SELECT FOR UPDATE ON idempotency_records`)
   - `User Counter` lock (`SELECT FOR UPDATE ON show_user_counters`)
   - `Seat Row` locks (`SELECT FOR UPDATE ON show_seats` in sorted order)
3. **All-or-Nothing Transaction Boundaries**: The entire operation runs inside `@Transactional(isolation = Isolation.READ_COMMITTED)`. If any requested seat is already held or reserved by another transaction, the method throws a `SeatConflictException` (HTTP 409), causing Spring's transaction manager to issue an immediate `ROLLBACK`. No partial holds are persisted.

---

## 2. Idempotency Architecture

### Storage & Schema
Idempotency records are stored in the database in the `idempotency_records` table, enforced by a unique composite constraint:
```sql
CONSTRAINT unique_show_user_idempotency UNIQUE (show_id, user_id, idempotency_key)
```

### Exactly-Once Enforcement Flow
1. **Pessimistic Acquisition**: Before processing a reservation, the application attempts to insert a `PROCESSING` record into `idempotency_records`.
2. **Locking existing key**: It performs `SELECT FOR UPDATE` on `(show_id, user_id, idempotency_key)`.
3. **Idempotent Replay**: If the status is `COMPLETED` and the request payload hash matches, the previously generated JSON response payload (`ReservationResultDto`) is returned immediately with HTTP 200/201 without mutating seat state or incrementing user counters.
4. **Key Reuse Conflict**: If the same idempotency key is supplied with a different seat list (different request hash), the system rejects the request with HTTP **409 Conflict** (`"Idempotency key reused with different seat list"`).

---

## 3. Holds & Expiry Model

### Explicit Cancellation Model
We implement an **Explicit Cancellation Model** via `POST /reservations/{id}/cancel` (and `DELETE /reservations/{id}`). Only the authentic owner (authenticated via JWT) may cancel their own confirmed reservation.

### Safety Guarantee Against Resurrecting Confirmed Seats
A potential edge-case bug in cancellation logic occurs when User A cancels a reservation for Seat A12 while User B has already purchased or locked Seat A12 in a separate workflow.

To guarantee that cancellation never resurrects a seat already confirmed to someone else:
1. The cancellation transaction locks the `reservation` row using `SELECT FOR UPDATE`.
2. It verifies `user_id` matches the token user ID (or throws `403 Forbidden`).
3. It acquires row locks on `show_user_counters` and `show_seats`.
4. It updates the seat status to `AVAILABLE` **only if the seat status is currently `RESERVED` or `CONFIRMED` for that specific reservation**. If the seat status was altered or blocked by another entity, the seat status is preserved intact.
5. Decrements `show_user_counters` and increments `available_seats` count on `shows`.

---

## 4. Consistency vs. Availability Under Partition (CAP Theorem Analysis)

In the CAP theorem context, the Paytm Seat Reservation System prioritizes **Consistency and Partition Tolerance (CP)** over high availability (AP).

### Rationale: Zero Double-Sell Invariant
In high-concurrency ticket sales, selling the exact same physical seat twice (double-selling) results in severe customer dissatisfaction, legal breach, and financial penalty. Giving a clean HTTP 409 decline ("Seat Taken") is acceptable; confirming a non-existent or duplicate seat is catastrophic.

### System Behavior During Network Partitions
- If a network partition isolates a database node or network region, nodes that cannot establish a quorum or acquire row locks fail safe by returning HTTP 503 / 409 declines rather than accepting split-brain reservations.
- Strong ACID transactions via PostgreSQL guaranteed by primary row-level pessimistic locking ensure linearizable consistency across all concurrent requests.

---

## 5. Observability & Alerting Strategy

### 1. Prometheus Metrics (`/actuator/prometheus`)
- `reservations_total{outcome="confirmed"}` — Counter of successful seat confirmations.
- `reservations_total{outcome="declined", reason="seat-taken"}` — Counter of seat conflict declines (409).
- `reservations_total{outcome="declined", reason="per-user-limit"}` — Counter of per-user quota declines (409).
- `reservations_total{outcome="declined", reason="idempotent-replay"}` — Counter of idempotent cache replays.
- `reservations_total{outcome="declined", reason="idempotency-key-reuse"}` — Counter of key mismatch declines (409).
- `seats_available` — Dynamic gauge tracking remaining available seats across all shows.
- `reservation_duration_seconds` — Timer metric tracking latency distribution (p50, p95, p99).

### 2. Health & Dependency Probes
- **`/livez`** (Liveness): Returns `200 OK` if JVM process is alive (does not ping database to prevent cascading health check failure during DB load).
- **`/readyz`** (Readiness): Queries `SELECT 1` against PostgreSQL. Fails closed (`503 Service Unavailable`) if the database connection pool is exhausted or database is unreachable.

### 3. Structured Logging & Correlation IDs
- Logging is configured with JSON format via `logback-spring.xml`.
- `RequestLoggingFilter` injects a unique `X-Request-ID` UUID into standard `MDC` (Mapped Diagnostic Context) for every HTTP request.
- `LoggingAspect` provides dynamic AOP method entry/exit tracing across Controller, Service, and Repository layers with exact millisecond timing.

### 4. What Triggers a 2 AM Page?
1. **Readiness Failure (`/readyz` returning 503 for > 30 seconds)**: Database pool exhaustion or DB downtime.
2. **Elevated 5xx Error Rate (`5xx > 0.1%` of total requests)**: Indicates unhandled runtime errors or database deadlock exceptions.
3. **Reconciliation Invariant Alert (`available + held + confirmed != total_seats`)**: Triggers an immediate critical page if database state corrupts.
4. **Latency Spike (`reservation_duration_seconds p99 > 500ms`)**: DB lock contention bottleneck warning.

---

## 6. AI Usage Disclosure

### AI Directed vs. AI Decided Breakdown
- **AI Decided (Architectural Foundations)**:
  - Decision to use PostgreSQL row-level pessimistic locks (`SELECT ... FOR UPDATE`) with deterministic lexicographical seat sorting for deadlock prevention.
  - Designing the `show_user_counters` atomic table for per-user quota enforcement under high concurrency.
- **AI Directed (Implementation Assistance)**:
  - Writing boilerplate Spring Boot configuration, Flyway migration SQL schemas, and JSON logging aspect (`LoggingAspect`).
  - Generating stress test scenario automation script (`scripts/burst.py`) with ThreadPoolExecutor multi-threading.
  - Formulating Testcontainers setup for JUnit integration tests (`PostgresConcurrencyIntegrationTests.java`).

---

## 7. Future Scalability Roadmap

1. **Redis Distributed Locking / Lua Scripts**: Offload row-level seat locking from PostgreSQL to Redis cluster using Redis Lua scripts for sub-millisecond lock acquisition during initial stampede.
2. **Database Sharding by Show ID**: Partition PostgreSQL instances by `show_id` so that on-sale events for different concerts/shows operate on independent database hardware.
3. **Async Event Streaming (Kafka)**: Emit reservation confirmation events to Apache Kafka for asynchronous notification delivery (email/SMS ticket generation) without slowing down the core transaction path.
4. **Optimistic Locking for Non-Hot Shows**: Implement optimistic locking (`version` column check) for regular shows, dynamically switching to pessimistic locking only when hot-seat contention exceeds a threshold.
