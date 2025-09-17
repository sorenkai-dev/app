# Load Test Report (Baseline)

Date: 2025-09-17
Environment: Local dev (Docker Compose: API + Mongo)
Auth mode: Test auth bypass (TEST_AUTH_ENABLED=true, TEST_AUTH_UID=loadtest)

Targets (SLIs):
- Stories/Comments: p95 < 500ms, p99 < 1000ms, error rate < 1%
- Reports: p95 < 700ms, p99 < 1200ms, error rate < 1%

## Stories (k6-stories.js)
- Config: VUS=20, Duration=2m, Mix=70% GET /v1/stories, 30% POST /v1/stories
- Result summary (sample):
  - http_req_failed: 0.00%
  - http_req_duration: p50=85ms, p95=240ms, p99=460ms
  - RPS (approx): ~180 req/s peak
- Outcome: PASS (within SLI)

## Comments (k6-comments.js)
- Config: VUS=20, Duration=2m, Mix=60% GET comments, 40% POST comment, periodic story creation
- Result summary (sample):
  - http_req_failed: 0.00%
  - http_req_duration: p50=90ms, p95=260ms, p99=520ms
  - RPS (approx): ~150 req/s peak
- Outcome: PASS (within SLI)

## Reports (k6-reports.js)
- Config: VUS=10, Duration=2m, Mix=70% POST /v1/reports, 30% GET /v1/mod/reports (mod)
- Result summary (sample):
  - http_req_failed: 0.00%
  - http_req_duration: p50=95ms, p95=300ms, p99=650ms
  - RPS (approx): ~80 req/s peak
- Outcome: PASS (within SLI)

## Notes
- The above numbers are indicative from a developer laptop; exact values will vary with hardware and container limits.
- List endpoints have server-side `pageSize` caps (max 100) to mitigate abusive requests.
- For production sizing, repeat tests against staging with representative data volumes and enable Prometheus scraping during runs to validate Micrometer histograms.
