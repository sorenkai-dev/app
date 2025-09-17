import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<700', 'p(99)<1200'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH_USER = __ENV.AUTH_USER || 'Bearer user-token';
const AUTH_MOD = __ENV.AUTH_MOD || 'Bearer mod-token';

// Requires backend started with test auth bypass for user and mod roles.
// For mod paging, set TEST_AUTH_ROLES=mod for requests using AUTH_MOD.

export default function () {
  // 70% create report as user, 30% mod paging
  if (Math.random() < 0.7) {
    const headers = { Authorization: AUTH_USER, 'Content-Type': 'application/json' };
    const payload = {
      targetType: 'story',
      targetId: Math.random().toString(36).slice(2),
      reason: 'spam',
    };
    const res = http.post(`${BASE}/v1/reports`, JSON.stringify(payload), { headers });
    check(res, { 'POST /v1/reports 201': (r) => r.status === 201 });
  } else {
    const headers = { Authorization: AUTH_MOD };
    const res = http.get(`${BASE}/v1/mod/reports?status=open&pageSize=20`, { headers });
    check(res, { 'GET /v1/mod/reports 200': (r) => r.status === 200 || r.status === 403 });
  }
  sleep(0.1);
}
