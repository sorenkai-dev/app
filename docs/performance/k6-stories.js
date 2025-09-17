import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const AUTH = __ENV.AUTH || 'Bearer test-token';

// Requires backend started with test auth bypass:
//   TEST_AUTH_ENABLED=true TEST_AUTH_UID=loadtest TEST_AUTH_ROLES=user
// Note: Authorization header must be present but token value is not validated in test mode.

export default function () {
  const headers = {
    Authorization: AUTH,
    'Content-Type': 'application/json',
  };

  // 70% GET, 30% POST
  if (Math.random() < 0.3) {
    const story = {
      title: `k6 story ${Math.random().toString(36).slice(2)}`,
      url: `https://example.com/${Math.random().toString(36).slice(2)}`,
      summary: 'Performance testing story',
      tags: ['k6', 'perf'],
    };
    const res = http.post(`${BASE}/v1/stories`, JSON.stringify(story), { headers });
    check(res, {
      'POST /v1/stories status is 201': (r) => r.status === 201,
    });
  } else {
    const res = http.get(`${BASE}/v1/stories?pageSize=20`);
    check(res, {
      'GET /v1/stories status is 200': (r) => r.status === 200,
    });
  }

  sleep(0.1);
}
