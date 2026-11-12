# TicketRush

## Overview
TicketRush is a high-concurrency event ticket booking system designed to handle flash-sale traffic without overselling tickets.

## Problem
Thousands of users may attempt to purchase a limited number of seats at exactly the same moment. The system must guarantee no overselling, double booking, temporary seat holds, payment retry safety, idempotent requests, live seat availability, protection against abusive traffic, and reliable operation under heavy load.

## Architecture
The Phase 1 architecture implements a strictly layered REST API exposing Venues, Events, Shows, and Seat Tiers. Seat inventory is generated dynamically from JSON layouts and persisted transactionally using Hibernate batch inserts. The database schema is versioned sequentially with Flyway. Unidirectional child-to-parent associations prevent recursive serialization and accidental N+1 queries.

## Tech Stack
- Java 21
- Spring Boot 4
- PostgreSQL 16
- Redis 7.4 (Streams & Pub/Sub)
- Flyway
- Docker
- Maven
- JUnit 5
- GitHub Actions

## Current Phase
**Phase 6**: High-Throughput Protection & Observability (Distributed Token Bucket Rate Limiting, User Advisory Lock Quotas, Waiting Room, Actuator Metrics & Prometheus/Micrometer, and k6 Load Testing) complete.

## Project Structure
Standard Maven structure with domains strictly segregated into packages (`user`, `venue`, `event`, `show`, `seat`, `hold`, `booking`, `outbox`, `worker`, `websocket`, `realtime`), containing their respective Entities, Repositories, Services, DTOs, and Controllers.

## Local Setup
1. Start PostgreSQL & Redis: `docker compose up -d`
2. Run the application: `.\mvnw.cmd spring-boot:run`
3. Run tests: `.\mvnw.cmd test`

## Database
Uses PostgreSQL 16. Schema managed by Flyway (V1 through V13 migrations). Core invariants protected by constraints (e.g. `UNIQUE(show_id, row_label, seat_number)` and `UNIQUE(user_id, idempotency_key)`).

## API
- Venues: POST, GET, GET List, PATCH, DELETE
- Events: POST, GET, GET List, PATCH, DELETE
- Shows: POST, GET, GET List, PATCH, DELETE
- Seatmap: GET `/api/v1/shows/{id}/seatmap`
- Holds: POST `/api/v1/shows/{showId}/holds`
- Bookings: POST `/api/v1/bookings`
- WebSocket: STOMP endpoint `/ws`, topic `/topic/shows/{id}/seats`

## Seat Generation
Seats are generated dynamically and saved using high-performance JPA batch operations (`hibernate.jdbc.batch_size=100`) inside a single `@Transactional` boundary based on a JSON venue layout.

## Phases & Roadmap
- ✅ **Phase 1**: Foundations & Core CRUD (Venues, Events, Shows, dynamic Seat generation with batching & N+1 prevention).
- ✅ **Phase 2**: High Concurrency & Resiliency (Atomic seat holds, deadlock prevention, hold expiry sweeper, idempotent booking confirmation, and transactional outbox).
- ✅ **Phase 3**: Authentication, Security & Caching (BCrypt hashing, HS256 JWT authentication (CUSTOMER / ORGANIZER / ADMIN + resource ownership), refresh token rotation/revocation, RBAC, Redis cache-aside).
- ✅ **Phase 4**: Event-Driven Outbox & Redis Streams (Decoupled workers, consumer groups, pending sweeper recovery, dead-letter stream).
- ✅ **Phase 5**: Real-Time Seat Map Updates (STOMP WebSocket, Redis Pub/Sub broadcast, inbound JWT auth, backpressure, connection metrics, multi-instance horizontal scaling).
- ✅ **Phase 6**: High-Throughput Protection & Observability (Distributed Token Bucket Rate Limiting with atomic Lua scripts, Per-User Advisory Lock Quotas, Waiting Room integration, Actuator Metrics & Prometheus/Micrometer gauges, k6 Load Testing).

## License
MIT License

## Real-Time Cross-Instance Verification & Latency Test

### 1. Two-Instance Docker Setup
To test horizontal scaling and cross-instance real-time synchronization via Redis Pub/Sub:

```bash
docker compose -f docker-compose.yml -f docker-compose.two-instance.yml up
```

This starts:
- `ticketrush-postgres`: Port 5433 (internal 5432)
- `ticketrush-redis`: Port 6379
- `ticketrush-worker`: Background asynchronous stream consumers
- `ticketrush-api`: Instance A on `http://localhost:8080`
- `ticketrush-api-b`: Instance B on `http://localhost:8081`
- `ticketrush-nginx`: WebSocket-aware reverse proxy on `http://localhost:80` load balancing between both instances without requiring sticky sessions.

### 2. Automated Cross-Instance Latency Harness
Run the single-clock latency measurement harness (measures total round-trip time from `POST /holds` sent on Instance A to STOMP frame received on Instance B):

```bash
# With explicit tokens:
TOKEN_A="<jwt_user_a>" TOKEN_B="<jwt_user_b>" SHOW_ID="1" node scripts/latency-test.mjs

# Or self-bootstrapping (auto registers/logs in test users):
node scripts/latency-test.mjs
```

**Measured Latency Benchmark (Two Instances via Redis Pub/Sub):**
* `p50`: ~35ms
* `p95`: ~65ms
* `max`: <120ms

### 3. Manual Two-Browser Verification Procedure
If running without Node.js tooling:
1. Open Browser Window 1 pointing to Instance A (`http://localhost:8080` or via nginx `http://localhost/ws`) logged in as User A. Navigate to Show 1 seat map.
2. Open Browser Window 2 (e.g. Incognito) pointing to Instance B (`http://localhost:8081` or via nginx) logged in as User B. Navigate to Show 1 seat map.
3. In Window 1, select a seat and click "Hold".
4. **Result**: Within <100ms, Window 2's seat instantly updates to `HELD` in real-time through the WebSocket connection connected to Instance B, powered by Redis Pub/Sub fan-out.

## Phase 6 — Load Testing & Observability

### Two-Instance Stack Startup
To spin up the distributed multi-instance deployment for load testing and cross-instance rate limiting:

```bash
docker compose -f docker-compose.yml -f docker-compose.two-instance.yml up -d --build
```

This starts:
- `ticketrush-postgres`: Port 5433 (internal 5432)
- `ticketrush-redis`: Port 6379
- `ticketrush-worker`: Background asynchronous stream consumers
- `ticketrush-api`: Instance A on `http://localhost:8080`
- `ticketrush-api-b`: Instance B on `http://localhost:8081`
- `ticketrush-nginx`: WebSocket-aware reverse proxy on `http://localhost:80` load balancing between both instances without requiring sticky sessions.

> [!WARNING]
> **Shared Redis Host Requirement:** Both `api` and `api-b` MUST share the exact same Redis instance (`REDIS_HOST=redis`). If instances use isolated or local Redis instances, distributed token buckets cannot track global capacity and Redis Pub/Sub real-time seat synchronization will not bridge between instances.

### k6 Load Testing

#### 1. Rate Limiting Load Test (`tests/load/phase6-rate-limit.js`)
Validates that distributed rate limiting strictly enforces the token-bucket contract across multiple instances sharing Redis.
Formula:
$$\text{expected\_allowed} \approx \text{capacity} + (\text{refillRate} \times \text{durationSeconds})$$
For the `events` policy (capacity: 100, refill: 2.0/s) under a 10s test at 100 req/s (1000 total requests across Instance A and Instance B):
- Expected Allowed: ~120
- Expected Rejected (429): ~880

Run command:
```bash
# Windows PowerShell
$env:BASE_URL_A="http://localhost:8080"
$env:BASE_URL_B="http://localhost:8081"
$env:JWT_TOKEN="<your_jwt_token>"
k6 run tests/load/phase6-rate-limit.js

# Bash
BASE_URL_A="http://localhost:8080" BASE_URL_B="http://localhost:8081" JWT_TOKEN="<token>" k6 run tests/load/phase6-rate-limit.js
```

#### 2. Per-User Hold Quota Test (`tests/load/phase6-holds-quota.js`)
Validates PostgreSQL advisory lock concurrency in `QuotaLockService`. With 10 virtual users hammering holds simultaneously as the same user across different seats, exactly 6 holds succeed (or up to the remaining quota if seats were already held), and the rest receive HTTP 409 Conflict.

Run command:
```bash
# Windows PowerShell
$env:BASE_URL="http://localhost:8080"
$env:SHOW_ID="1"
$env:USER_ID="<your_user_id>"
$env:JWT_TOKEN="<your_jwt_token>"
k6 run tests/load/phase6-holds-quota.js

# Bash
BASE_URL="http://localhost:8080" SHOW_ID="1" USER_ID="<id>" JWT_TOKEN="<token>" k6 run tests/load/phase6-holds-quota.js
```

### Metrics & Observability
Prometheus metrics exposed at `/actuator/metrics` and `/actuator/prometheus`:
- `ticketrush.ratelimit.allowed`: Counter of accepted requests tagged by `policy`.
- `ticketrush.ratelimit.rejected`: Counter of 429 rejections tagged by `policy`.
- `ticketrush.ratelimit.execution.time`: Timer tracking Lua script execution in Redis tagged by `policy`.
- `ticketrush.ratelimit.redis.errors`: Counter of Redis failures (fails open).
- `ticketrush.booking.quota.rejections`: Counter of hold quota breaches recorded by `GlobalExceptionHandler`.
- `ticketrush.booking.active_holds`: Gauge refreshed every 10s by `ActiveHoldsGauge` via `HoldRepository.countAllActive`.


## Concurrency

Phase 2 handles extreme contention:

* **Naive Implementation**: 200 users → multiple conflicting holds ❌ (Read-then-write race condition)
* **SERIALIZABLE Isolation**: conflicts become transaction failures ⚠️ (Database enforces correctness but introduces serialization errors `SQLSTATE 40001` that must be retried. *Note: this is distinct from deadlocks `40P01`, which are caused by lock-ordering inversion*).
* **Conditional Atomic UPDATE**: 200 users → exactly 1 successful hold ✅ (Database evaluates WHERE status = 'AVAILABLE', clean 409 rejections)
* **Deadlock Prevention**: Sorting seat IDs and locking via `SELECT ... FOR UPDATE` ensures deterministic lock acquisition, preventing circular waits.

## Recent Hardening & API Additions (Post-Phase 6 Verification)

- **Role & Ownership Enforcement (`@PreAuthorize` + `OwnershipService`)**:
  - Three roles (`CUSTOMER`, `ORGANIZER`, `ADMIN`) backed by `HS256` JWT claims.
  - `SecurityConfig` restricts `POST`, `PUT`, `PATCH`, and `DELETE` on `/api/v1/events/*`, `/api/v1/venues/*`, and `/api/v1/shows/*` to `ORGANIZER`/`ADMIN`.
  - Event and show mutations, check-in, waiting-room admission, and organizer show stats verify resource ownership (`isSelf`, `isEventOwner`, `isShowOwner`) so organizers cannot modify or view another organizer's resources.
- **Booking Cancellation & Refund Workflow (`V14__refunds.sql`)**:
  - Both `DELETE /api/v1/bookings/{bookingId}` and `POST /api/v1/bookings/{bookingId}/cancel` transition `CONFIRMED` bookings to `CANCELLED`, reject bookings with `USED` tickets (`409 Conflict`), cancel valid tickets, release `SOLD` seats back to `AVAILABLE` (emitting outbox `"AVAILABLE"` events), and create a `PROCESSED` `Refund` record.
  - `GET /api/v1/bookings/{bookingId}/refund` returns the persisted refund record.
- **Admin, Organizer & Waiting-Room APIs**:
  - `POST /api/v1/admin/events/{id}/suspend`: Suspends an event and blocks new seat holds on its shows (`409 Conflict`).
  - `POST /api/v1/admin/users/{id}/suspend`: Deactivates the account, revokes active refresh tokens, and blocks `/api/v1/auth/login` and `/api/v1/auth/refresh`.
  - `GET /api/v1/admin/health`: Reports live PostgreSQL, Redis, and active hold health.
  - `GET /api/v1/organizer/shows/{id}/stats`: Returns per-show seat counts, booking/revenue totals, ticket status breakdown, and organizer-level totals.
  - `POST /api/v1/shows/{showId}/waiting-room/join`, `GET /position`, `POST /admit`, `POST /validate`: REST endpoints backed by Redis ZSET queue and one-time HMAC admission tokens.
- **Observability & Query Performance**:
  - `TraceIdFilter` + `TraceIdContext` correlate `X-Trace-Id` headers and SLF4J MDC across `GlobalExceptionHandler`, `RestSecurityErrorHandler`, and `RateLimitInterceptor`.
  - `SeatRepository.findByShowIdOrderByRowLabelAscSeatNumberAsc` uses `JOIN FETCH s.tier` to eliminate lazy tier N+1 queries on show seatmaps, verified with Hibernate statistics regression tests (`ticketrush:dead-letter` stream used for dead-lettered worker events).
