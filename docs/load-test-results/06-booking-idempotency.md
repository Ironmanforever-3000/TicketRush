# Phase 2.6 — Booking Confirmation

## Goal

Convert valid held seats into sold seats exactly once.

## Idempotency

Client sends:

Idempotency-Key: UUID

The database enforces:

UNIQUE(idempotency_key)

A duplicate request returns the existing booking with HTTP 200.

## Seat transition

HELD → SOLD

Only when:
- seat belongs to requested show
- seat is currently HELD
- held_by matches current user
- hold has not expired

## Transaction

The following occur in one database transaction:
- booking creation
- seat transition
- booking_seats creation
- booking confirmation
- outbox event creation

## Tests

### Successful booking
Result: PASS

### Duplicate request
Result: PASS

### Concurrent same-key requests
Result: PASS

### Concurrent different-key requests for same seat
Result: PASS

### Partial multi-seat failure
Result: PASS

### Expired hold
Result: PASS

### Wrong user
Result: PASS
