# Performance and Load Testing

This folder contains reproducible k6 scripts to baseline API performance and validate SLIs before production.

Tools:
- k6 (https://k6.io) – can be installed locally or run via Docker.

Safety notes:
- These scripts assume running against a non-production environment.
- For endpoints that require authentication, enable the test auth bypass (only for non-prod):
  - Set env vars: TEST_AUTH_ENABLED=true, TEST_AUTH_UID=loadtest, TEST_AUTH_ROLES=user (or mod for moderator flows).
  - Still include an Authorization header (any Bearer token string). In prod, this bypass is disabled.

## Scripts

- k6-stories.js
  - Mix of GET /v1/stories (70%) and POST /v1/stories (30%).
- k6-comments.js
  - Creates stories periodically and exercises POST/GET comments.
- k6-reports.js
  - Creates reports as user and pages reports as moderator (requires TEST_AUTH_ROLES=mod for mod requests).

All scripts expose common environment variables:
- BASE_URL: base URL of the API (default http://localhost:8080)
- VUS: number of virtual users (default 20 for stories/comments, 10 for reports)
- DURATION: total test duration (default 2m)
- AUTH / AUTH_USER / AUTH_MOD: bearer token string to include; required by test-bypass logic

## Running with local k6

Example: Stories baseline at 20 VUs for 2 minutes

```
export TEST_AUTH_ENABLED=true
export TEST_AUTH_UID=loadtest
export TEST_AUTH_ROLES=user
export BASE_URL=http://localhost:8080
export AUTH="Bearer dev"

k6 run docs/performance/k6-stories.js
```

Comments flow:
```
export AUTH="Bearer dev"
k6 run -e BASE_URL=http://localhost:8080 docs/performance/k6-comments.js
```

Reports flow (moderator paging):
```
export TEST_AUTH_ROLES=mod
export AUTH_USER="Bearer dev-user"
export AUTH_MOD="Bearer dev-mod"
k6 run -e BASE_URL=http://localhost:8080 docs/performance/k6-reports.js
```

## Running with Docker

```
docker run --rm -i grafana/k6 run - < docs/performance/k6-stories.js
```

Pass environment variables with `-e`, e.g., `-e BASE_URL=http://host.docker.internal:8080 -e AUTH="Bearer dev" -e VUS=50 -e DURATION=5m`.

## SLIs and thresholds

- Success rate: http_req_failed < 1%
- Latency: http_req_duration p95 < 500ms, p99 < 1s (stories/comments); p95 < 700ms, p99 < 1.2s (reports)

These thresholds are embedded in the scripts; k6 will fail the run if violated.

## Notes

- List endpoints cap `pageSize` to 100 server-side to prevent abusive requests and protect performance.
- Metrics are exposed at /metrics; consider scraping during tests for richer analysis (RPS, error rates, latency histograms).
- For heavier scenarios, gradually increase VUs and duration, and monitor Mongo resource usage (connections, CPU, IOPS).
