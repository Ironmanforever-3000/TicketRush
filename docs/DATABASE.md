# Database Architecture

We use PostgreSQL 16 managed by Flyway.

## Current Schema (Phase 1)
- `users`: Organizers and customers.
- `venues`: Physical locations with a JSON layout definition.
- `events`: Conceptual events (e.g., "Taylor Swift Eras Tour").
- `shows`: Specific instances of an event at a venue at a given time.
- `seat_tiers`: Pricing categories for a specific show.
- `seats`: Generated inventory for a show.

## Critical Constraints
- `uq_show_seat`: `UNIQUE(show_id, row_label, seat_number)` guarantees a seat cannot exist twice.
- Foreign keys strongly associate seats -> tiers -> shows -> events/venues.
