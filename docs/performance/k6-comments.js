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

function createStory(headers) {
  const story = {
    title: `k6 comments ${Math.random().toString(36).slice(2)}`,
    url: `https://example.com/${Math.random().toString(36).slice(2)}`,
    summary: 'Story for comments perf test',
    tags: ['k6', 'comments'],
  };
  const res = http.post(`${BASE}/v1/stories`, JSON.stringify(story), { headers });
  check(res, { 'story created': (r) => r.status === 201 });
  try {
    const body = res.json();
    return body && body.id;
  } catch (e) {
    return null;
  }
}

export default function () {
  const headers = {
    Authorization: AUTH,
    'Content-Type': 'application/json',
  };

  // Create a story occasionally to spread writes
  let storyId;
  if (Math.random() < 0.2) {
    storyId = createStory(headers);
  }

  // 60% GET comments, 40% POST comment
  if (Math.random() < 0.4) {
    // need a story id; if not created in this iteration, fetch one from list
    if (!storyId) {
      const list = http.get(`${BASE}/v1/stories?pageSize=1`).json();
      storyId = list && list.items && list.items[0] && list.items[0].id;
    }
    if (storyId) {
      const payload = { body: `nice! ${Math.random().toString(36).slice(2)}` };
      const res = http.post(`${BASE}/v1/stories/${storyId}/comments`, JSON.stringify(payload), { headers });
      check(res, { 'POST comment 201': (r) => r.status === 201 });
    }
  } else {
    if (!storyId) {
      const list = http.get(`${BASE}/v1/stories?pageSize=1`).json();
      storyId = list && list.items && list.items[0] && list.items[0].id;
    }
    if (storyId) {
      const res = http.get(`${BASE}/v1/stories/${storyId}/comments?pageSize=20`);
      check(res, { 'GET comments 200': (r) => r.status === 200 });
    }
  }

  sleep(0.1);
}
