# SERIALIZABLE Hold Concurrency Test

## Configuration

Virtual users: 200
Duration: 10 seconds
Target show: 1
Target seat: 42

Transaction isolation:

SERIALIZABLE

## Expected behavior

Concurrent conflicting transactions should not all
successfully commit as though they independently acquired
the same inventory.

## k6 results

HTTP requests:
5061

Successful requests:
Failed requests:
5060

Average latency:
392.09ms

p95 latency:
763.66ms

p99 latency:
(not extracted, but ~1.5s based on max 2.12s)

## PostgreSQL result

Successful hold rows claiming seat 42:
## Serialization failures

~5060 serialization failures (SQLSTATE: 40001).
Exception: \ERROR: could not serialize access due to concurrent update\

## Result

SERIALIZABLE prevented the naive concurrent behavior by
rejecting conflicting transactions, but introduced
serialization failures under contention.

## Conclusion

SERIALIZABLE improves correctness but creates contention
and requires application-level retry handling for a
production-quality implementation.
