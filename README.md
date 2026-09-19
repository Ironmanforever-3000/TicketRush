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
- Flyway
- Docker
- Maven
- JUnit
- GitHub Actions

## Current Phase
**Phase 3.3**: Refresh Token + Rotation + Revocation complete.

## Project Structure
Standard Maven structure with domains strictly segregated into packages (`user`, `venue`, `event`, `show`, `seat`), containing their respective Entities, Repositories, Services, DTOs, and Controllers.

## Local Setup
1. Start PostgreSQL: `docker compose up -d`
2. Run the application: `.\mvnw.cmd spring-boot:run`
3. Run tests: `.\mvnw.cmd test`

## Database
Uses PostgreSQL 16. Schema managed by Flyway (V1 through V5 migrations). Core invariants protected by constraints (e.g. `UNIQUE(show_id, row_label, seat_number)`).

## API
- Venues: POST, GET, GET List, PATCH, DELETE
- Events: POST, GET, GET List, PATCH, DELETE
- Shows: POST, GET, GET List, PATCH, DELETE
- Seatmap: GET `/api/v1/shows/{id}/seatmap`
- Holds: POST `/api/v1/shows/{showId}/holds`

## Phase 1
Phase 1 establishes the rock-solid CRUD foundation, database constraints, strict JSON serialization models, custom exception handlers, and bean validation rules without prematurely introducing concurrency mechanisms.

## Seat Generation
Seats are generated dynamically and saved using high-performance JPA batch operations (`hibernate.jdbc.batch_size=100`) inside a single `@Transactional` boundary based on a JSON venue layout.

## Testing
We have proven the ability to generate a 500-seat show reliably, and validated application constraints with unit tests and local CLI E2E tests.

## N+1 Experiment
**BEFORE**: Calling `GET /api/v1/shows/1/seatmap` executed 1 query to fetch the seats, followed by 500 individual queries to fetch the associated Show and Venue.
**AFTER**: Implemented an `@EntityGraph` annotation on the ShowRepository query, reducing the database footprint to a single optimized query.

## Design Decisions
See `docs/DECISIONS.md`.

## Future Phases
- Phase 2: Concurrency, Seat Holds, Optimistic & Pessimistic Locking.
- Phase 3: Authentication and Security.
- Phase 4: Rate Limiting and Caching.

## License
MIT License

## Concurrency Experiment

Phase 2 load testing demonstrates how TicketRush handles extreme contention (200 concurrent users fighting for 1 seat):

* **Naive Implementation**: 200 users → multiple conflicting holds ❌ (Read-then-write race condition)
* **SERIALIZABLE Isolation**: conflicts become transaction failures ⚠️ (Database enforces correctness but introduces ~5000 serialization errors)
* **Conditional Atomic UPDATE**: 200 users → exactly 1 successful hold ✅ (Database evaluates WHERE status = 'AVAILABLE', clean 409 rejections)
