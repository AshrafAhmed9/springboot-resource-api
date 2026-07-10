import http from 'k6/http';
import { check, sleep } from 'k6';

// Warm-cache scenario: log in once, reuse the token for every request.
// Since the token is validated once and cached (Caffeine, TTL <= 60s),
// nearly every request after the first should hit the local cache instead
// of round-tripping to the Go auth service over gRPC.

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
