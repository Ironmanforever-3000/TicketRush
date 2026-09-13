# Phase 2.5 — Hold Expiration

## Objective

Automatically release seats whose hold has expired.

## Configuration

- Scheduler: 10 seconds
- Hold lifetime: 5 minutes

## SQL

```sql
UPDATE seats
SET
    status = 'AVAILABLE',
    held_by = NULL,
    hold_expires_at = NULL,
    version = version + 1
WHERE status = 'HELD'
  AND hold_expires_at < now()
RETURNING id;
```

## Test 1 — Single expired seat

Seat: 42
Initial state: HELD
Expired: YES

Expected: AVAILABLE
Actual: AVAILABLE

## Test 2 — Multiple expired seats

Seats: 42, 45

Expected returned IDs: `[42, 45]`
Actual: `[42, 45]`

## Test 3 — Non-expired seat

Expected: Seat remains HELD
Actual: Seat remains HELD

## Test 4 — SOLD seat with old expiry

Expected: Seat remains SOLD
Actual: Seat remains SOLD

## Multi-replica reasoning

Two application instances may execute the scheduled job.

The conditional UPDATE makes this safe because only rows still in HELD state are eligible for release.

## Decision

Do not introduce Redis locking yet.

Future implementation may use:
- Redis distributed lock
- `SELECT ... FOR UPDATE SKIP LOCKED`
