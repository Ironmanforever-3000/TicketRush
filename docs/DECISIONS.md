# Design Decisions

## 001 — Child-to-parent JPA relationships
We use mostly unidirectional child-to-parent relationships instead of bidirectional collections.

Reason:
- avoid huge object graphs
- reduce accidental lazy loading
- make query intent explicit
- reduce serialization problems

## 002 — Flyway owns schema changes
JPA Hibernate does not create schema. Flyway migrations are the source of schema evolution.

## 003 — Integer money representation
Money is stored as integer paise/cents rather than floating point numbers.

## Phase 2 — Naive concurrency experiment

The Seat entity contains an optimistic-locking version field from Phase 1.

For the initial race-condition experiment, the naive hold update
uses a repository-level update that does not include the version
predicate. This is intentional: the purpose of the experiment is
to reproduce the read-then-write race before implementing the
correct atomic solution.

The @Version mechanism remains part of the domain model and will
be evaluated separately as part of the concurrency/locking study.

## 004 — Deadlock Prevention via Seat Normalization

We establish a canonical ascending seat-ID order for multi-seat locking/acquisition operations, preventing circular wait caused by inconsistent lock ordering.

Example:
Requested: `[9, 5]`
Normalized: `[5, 9]`

Reason:
Two transactions acquiring the same seats in different orders can create a circular wait.

Transaction A: `5` $\rightarrow$ waits for `9`
Transaction B: `9` $\rightarrow$ waits for `5`

PostgreSQL detects this cycle and aborts one transaction.

By always acquiring seats in ascending ID order:
Transaction A: `5` $\rightarrow$ `9`
Transaction B: `5` $\rightarrow$ `9`

One transaction may wait for the other, but they cannot form this particular circular wait. This ordering rule is therefore a strict part of the concurrency contract for multi-seat operations.

## 005 — Hold Expiry Sweeper

The hold expiry sweeper runs every 10 seconds on each application instance.

Current design:
- PostgreSQL is the source of truth.
- Expired seats are released with one conditional UPDATE.
- The UPDATE only targets rows where:
  - `status = 'HELD'`
  - `hold_expires_at < now()`
- The query uses `RETURNING id` to obtain the exact seats released.

Multiple replicas may execute the sweeper simultaneously.

This is currently safe because the update is conditional and idempotent:
once one replica changes a seat from `HELD` to `AVAILABLE`, another replica no longer matches the `WHERE` clause.

We intentionally do not add a distributed lock at this stage.

Future options:
- Redis distributed lock
- `SELECT ... FOR UPDATE SKIP LOCKED`

## 006 - Booking Idempotency

Booking confirmation uses a client-provided UUID
Idempotency-Key.

The database enforces UNIQUE(idempotency_key).

The application does not rely on a check-then-insert
sequence for correctness.

The booking operation is transactional:

1. create booking
2. transition HELD -> SOLD
3. create booking_seats rows
4. create BOOKING_CONFIRMED outbox event
5. commit

A retry using the same idempotency key returns the existing
booking with HTTP 200.

A new idempotency key competing for the same held seat
must fail with SEAT_UNAVAILABLE.

The seat update also checks ownership and expiration directly
against the seats table.
