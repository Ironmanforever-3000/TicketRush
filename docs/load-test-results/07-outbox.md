# Phase 2.7 — Outbox Pattern

## Goal

Ensure that downstream systems eventually receive a `BOOKING_CONFIRMED` event reliably without risking data loss if the system crashes during the booking transaction. 

## Design

### 1. The Booking Transaction
The booking logic explicitly creates an `OutboxEvent` within the same `@Transactional` database commit as the booking data and seat transitions.

The `outbox` table looks like:
- `id` (PK)
- `event_type` (`BOOKING_CONFIRMED`)
- `aggregate_type` (`BOOKING`)
- `aggregate_id` (The ID of the Booking)
- `payload` (JSONB format including `booking_id`, `show_id`, `user_id`, `seat_ids`)
- `created_at`
- `published_at` (Initially `NULL`)

### 2. The Worker
The `OutboxWorker` runs every 5 seconds checking for:
`published_at IS NULL`

The worker pulls at most 100 unpublished events and publishes them sequentially.

### 3. Reliability Rules
1. **Never mark before publishing:** The worker strictly calls `publish()` *before* updating `published_at`.
2. **At-Least-Once Delivery:** If the system crashes after publishing but before the database `published_at` update commits, the event will be picked up again and re-published on the next run. Downstream consumers must be idempotent.
3. **Failure Isolation:** Publisher failures are caught locally. The event's `published_at` remains `NULL`, leaving the event in the queue for a retry loop.

## Test Results
1. `testBookingCreatesOutbox` - PASS - Outbox event properly written inside the `createBooking` transaction.
2. `testWorkerPublishesEventAndUpdatesPublishedAt` - PASS - Outbox Worker calls the publisher and sets `published_at` to the current timestamp.
3. `testPublisherFailureLeavesEventUnpublished` - PASS - Simulating a broker failure correctly leaves the `published_at = NULL`, and a subsequent successful worker execution recovers the event.
