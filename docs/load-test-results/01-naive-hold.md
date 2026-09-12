# Naive Hold Concurrency Test

## Test

Virtual users: 200
Duration: 10 seconds
Target show: 1
Target seat: 42

## Implementation

The initial implementation performs:

1. SELECT seat
2. Check status == AVAILABLE
3. UPDATE status = HELD
4. Create hold

The check and update are not atomic.

## Expected

Only one successful hold for seat 42.

## Actual

Hold rows claiming seat 42: 49

## Result

FAILED

## Reason

Multiple concurrent requests read the seat as AVAILABLE before
the seat state was changed by the other transaction.

## Evidence

PostgreSQL query result:
```
 count 
-------
    49
(1 row)
```
