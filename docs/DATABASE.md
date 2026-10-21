# Database Architecture

We use PostgreSQL 16 managed by Flyway.

## Current Schema (Phases 1 - 3, Migrations V1 - V8)
- `users`: Organizers, customers, and admins (with BCrypt password hash and role).
- `venues`: Physical locations with a JSON layout definition.
- `events`: Conceptual events (e.g., "Taylor Swift Eras Tour").
- `shows`: Specific instances of an event at a venue at a given time.
- `seat_tiers`: Pricing categories for a specific show.
- `seats`: Generated inventory for a show with status (`AVAILABLE`, `HELD`, `BOOKED`), optimistic locking version, `held_by`, and `hold_expires_at`.
- `holds`: Seat holds with expiration timestamps.
- `bookings`: Booking transactions with idempotency key `(user_id, idempotency_key)` and status (`PENDING`, `CONFIRMED`, `FAILED`).
- `booking_seats`: Join table linking bookings to seats.
- `outbox_events`: Transactional outbox table for reliable at-least-once message publishing.
- `refresh_tokens`: Persisted refresh tokens supporting rotation, expiry, and revocation.

## Critical Constraints & Invariants
- `uq_show_seat`: `UNIQUE(show_id, row_label, seat_number)` guarantees a seat cannot exist twice.
- `uk_bookings_user_id_idempotency_key`: `UNIQUE(user_id, idempotency_key)` guarantees idempotency scoped per user.
- `chk_seats_held_state`: `CHECK((status = 'HELD' AND held_by IS NOT NULL AND hold_expires_at IS NOT NULL) OR (status != 'HELD'))`.
- Foreign keys strongly associate seats -> tiers -> shows -> events/venues.
