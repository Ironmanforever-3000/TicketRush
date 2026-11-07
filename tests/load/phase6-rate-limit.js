// tests/load/phase6-rate-limit.js
//
// Token bucket general behavior formula:
// expected_allowed ≈ capacity + (refillRate × durationSeconds)
//
// For the REAL "events" policy (capacity=100, refill=2.0/sec) configured in RateLimitConfig:
// Offered load: 100 req/sec for 10s = 1000 total requests
// expected_allowed ≈ 100 + (2.0 × 10) = ~120
// expected_rejected ≈ 1000 - 120 = ~880
//
// Note: If you change test rate, duration, or the events policy configuration,
// recompute expected counts using the formula above.

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const allowed = new Counter('rate_limit_allowed');
const rejected = new Counter('rate_limit_rejected');
const unexpected = new Counter('rate_limit_unexpected');

export const options = {
    scenarios: {
        rate_limit_test: {
            executor: 'constant-arrival-rate',
            rate: 100, timeUnit: '1s', duration: '10s',
            preAllocatedVUs: 100, maxVUs: 300,
        },
    },
};

export default function () {
    const baseUrl = (__ITER % 2 === 0) ? __ENV.BASE_URL_A : __ENV.BASE_URL_B;
    const response = http.get(`${baseUrl}/api/v1/events`, {
        headers: { Authorization: `Bearer ${__ENV.JWT_TOKEN}` },
    });
    if (response.status === 200) allowed.add(1);
    else if (response.status === 429) rejected.add(1);
    else unexpected.add(1);
    check(response, { 'status is 200 or 429': (r) => r.status === 200 || r.status === 429 });
}
