import http from 'k6/http';
import { check, sleep } from 'k6';

// Cold-cache scenario: identical to warm-cache.js, but run against an
// instance started with APP_CACHE_MAX_TTL_SECONDS=0 (see docker-compose
// override / README), so every request forces a fresh gRPC ValidateToken
// call instead of hitting the Caffeine cache. This isolates the cost of
// the gRPC round trip itself, for a direct warm-vs-cold comparison.

export const options = {
    vus: 10,
    duration: '40s',
};

const AUTH_BASE_URL = __ENV.AUTH_BASE_URL || 'http://localhost:8080';
const NOTES_BASE_URL = __ENV.NOTES_BASE_URL || 'http://localhost:8081';

export function setup() {
    const res = http.post(
        `${AUTH_BASE_URL}/login`,
        JSON.stringify({ email: 'admin@app.com', password: 'admin123' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(res, { 'login succeeded': (r) => r.status === 200 });
    return { token: res.json('access_token') };
}

export default function (data) {
    const res = http.get(`${NOTES_BASE_URL}/api/notes`, {
        headers: { Authorization: `Bearer ${data.token}` },
    });
    check(res, { 'status is 200': (r) => r.status === 200 });
    sleep(0.1);
}
