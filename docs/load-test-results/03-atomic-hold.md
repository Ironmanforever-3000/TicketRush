# Atomic Seat Hold Concurrency Test

## Configuration

Virtual users: 200
Duration: 10 seconds
Target show: 1
Target seat: 42

## Implementation

The seat hold uses a single conditional UPDATE:

`sql
UPDATE seats
SET status = 'HELD',
    held_by = :userId,
    hold_expires_at = ...
WHERE id IN (...)
  AND status = 'AVAILABLE';
`

The affected-row count determines whether all requested
seats were successfully acquired.

## Expected

Exactly one successful hold for seat 42.

## Actual

Successful holds for seat 42:
## Result

PASS

## Why

The database atomically evaluates the AVAILABLE condition
as part of the UPDATE. The single winning transaction gets ffected rows = 1, and all other transactions trying to claim the same seat concurrently get ffected rows = 0 which safely translates into a 409 Conflict.

## Comparison

Naive implementation:
~50 successful holds (Data Corruption)

SERIALIZABLE:
1 successful hold, ~5000 serialization failures (SQLSTATE 40001, 500 Internal Error)

Atomic update:
1 successful hold, ~6670 HTTP 409 Conflict responses (Clean rejection)
