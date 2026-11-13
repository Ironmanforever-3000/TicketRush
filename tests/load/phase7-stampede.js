// tests/load/phase7-stampede.js
//
// Simulates a cache stampede: 500 concurrent virtual users requesting
// the same show's seatmap over 3 seconds right after cache invalidation.
//
// With naive cache (no lock):
//   500 concurrent misses trigger 500 identical SELECT queries against Postgres.
// With stampede protection (SET NX lock + 50ms retry):
//   Exactly 1 request acquires the lock and queries Postgres; the other 499
//   wait 50ms and read from Redis once populated. Postgres query count drops from ~500 to ~1-2.

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const success = new Counter('seatmap_cache_success');
const failure = new Counter('seatmap_cache_failure');
const hitRate = new Rate('seatmap_cache_success_rate');
const latency = new Trend('seatmap_response_duration');

export const options = {
    scenarios: {
        stampede: {
            executor: 'constant-vus',
            vus: 500,
            duration: '3s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'], // less than 5% error rate under 500 VU stampede
    },
};

export default function () {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    const showId = __ENV.SHOW_ID || '1';

    const headers = { 'Content-Type': 'application/json' };
    if (__ENV.JWT_TOKEN) {
        headers['Authorization'] = `Bearer ${__ENV.JWT_TOKEN}`;
    }

    const start = new Date();
    const res = http.get(`${baseUrl}/api/v1/shows/${showId}/seatmap`, { headers });
    latency.add(new Date() - start);

    const isOk = res.status === 200;
    if (isOk) {
        success.add(1);
        hitRate.add(1);
    } else {
        failure.add(1);
        hitRate.add(0);
    }

    check(res, {
        'status is 200': (r) => r.status === 200,
    });
}
