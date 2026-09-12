# Phase 2.4 — Deadlock Experiment

## Setup

Two PostgreSQL transactions were run concurrently on identical seats but in different orders.

**Transaction A:**
```sql
BEGIN;
SELECT id FROM seats WHERE id = 5 FOR UPDATE;
-- Wait 2 seconds
SELECT id FROM seats WHERE id = 9 FOR UPDATE;
COMMIT;
```

**Transaction B:**
```sql
BEGIN;
SELECT id FROM seats WHERE id = 9 FOR UPDATE;
-- Wait 2 seconds
SELECT id FROM seats WHERE id = 5 FOR UPDATE;
COMMIT;
```

## Result

PostgreSQL detected a deadlock and aborted Transaction A.

```text
ERROR:  deadlock detected
DETAIL:  Process 566 waits for ShareLock on transaction 2955; blocked by process 573.
Process 573 waits for ShareLock on transaction 2954; blocked by process 566.
HINT:  See server log for query details.
CONTEXT:  while locking tuple (0,9) in relation "seats"
SQL statement "SELECT id FROM seats WHERE id=9 FOR UPDATE"
```

## Cause

- Transaction A held seat 5 while waiting for 9.
- Transaction B held seat 9 while waiting for 5.

This created a circular wait. Neither could proceed.

## Prevention

Normalize multi-seat IDs before lock acquisition:

`[9,5]` $\rightarrow$ `[5,9]`

By always acquiring seats in ascending ID order, we ensure a consistent acquisition path. Two transactions acquiring the same subset of seats will acquire them in the exact same order. While one may wait for the other, they can never form a circular wait. This effectively prevents this class of deadlocks at the application boundary.
