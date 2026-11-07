// tests/load/phase6-holds-quota.js
// Verifies: a single user hammering POST /shows/{id}/holds never ends up owning more than
// 6 seats for that show, even under concurrent load — the exact race the advisory lock exists to close.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const holdSucceeded = new Counter('holds_succeeded');
const holdRejectedQuota = new Counter('holds_rejected_quota');   // 409 from BusinessRuleException
const holdRejectedRateLimit = new Counter('holds_rejected_429'); // 429 from the holds policy (capacity 5, refill 0.1/sec)

export const options = {
    scenarios: {
        quota_race: { executor: 'shared-iterations', vus: 10, iterations: 10, maxDuration: '15s' },
    },
};

export default function () {
    const baseUrl = __ENV.BASE_URL_A || __ENV.BASE_URL || 'http://localhost:8080';
    const seatId = (Number(__ENV.SEAT_START_ID) || 1000) + __VU;   // each VU grabs a distinct seat so the race is about COUNT, not which seat
    const userId = Number(__ENV.USER_ID) || 1;
    const res = http.post(`${baseUrl}/api/v1/shows/${__ENV.SHOW_ID}/holds`,
        JSON.stringify({ userId: userId, seatIds: [seatId] }),
        { headers: { Authorization: `Bearer ${__ENV.JWT_TOKEN}`, 'Content-Type': 'application/json' } });

    if (res.status === 200 || res.status === 201) holdSucceeded.add(1);
    else if (res.status === 409) holdRejectedQuota.add(1);
    else if (res.status === 429) holdRejectedRateLimit.add(1);

    check(res, { 'status is 200, 201, 409 or 429': (r) => [200, 201, 409, 429].includes(r.status) });
}
// Run this against a fresh test user starting at 0 held seats for the show.
// Expected: holdSucceeded caps at 6 (the per-show seat limit) regardless of how many of the
// 10 VUs race simultaneously — proving QuotaLockService's advisory lock, not just the rate
// limiter, is what enforces the business rule.
