import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

export const serializationOrConflict =
    new Counter('hold_conflict_responses');

export const successfulHolds =
    new Counter('successful_holds');

export const options = {
    vus: 200,
    duration: '10s',
};

const FIRST_TEST_USER_ID = 51;

export default function () {

    const userId =
        FIRST_TEST_USER_ID + __VU - 1;

    const payload = JSON.stringify({
        userId: userId,
        seatIds: [42],
    });

    const response = http.post(
        'http://host.docker.internal:8080/api/v1/shows/1/holds',
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    if (response.status === 201) {
        successfulHolds.add(1);
    } else {
        serializationOrConflict.add(1);
    }

    check(response, {
        'hold request completed': (r) =>
            r.status >= 200 && r.status < 600,
    });
}
