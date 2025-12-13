# SorenKai Backend

Backend API built with Ktor 3, Kotlinx Serialization, MongoDB, and Zitadel Auth.

## Deployment

### Option A: Google Cloud Run (recommended, low cost)

A step-by-step guide for Cloud Run + Artifact Registry + Secret Manager is available at:
- docs/deploy/cloud-run.md

This option offers scale-to-zero and minimal ops. You’ll still use MongoDB Atlas for the database.

### Option B: Kubernetes with Kustomize

Manifests are provided under `deploy/k8s`:
- `deployment.yaml`: Non-root, read-only FS (tmp writable), probes to `/serverhealth`, env/secret wiring, zero-downtime rolling updates enabled (maxUnavailable=0, maxSurge=1), lifecycle preStop drain, and terminationGracePeriodSeconds set.
- `service.yaml`: ClusterIP service exposing port 80 -> container 8080.
- `configmap.yaml`: Non-sensitive configuration (ENV, ALLOWED_ORIGINS, MAX_UPLOAD_MB, shutdown).
- `secrets.yaml`: Template for sensitive values (Mongo URI) and Google SA secret placeholder.
- `kustomization.yaml`: Ties resources together and allows image tag override.

Quick start (staging example):
1. Edit `deploy/k8s/configmap.yaml` to set `ENV`, `ALLOWED_ORIGINS`, and RTDB URL as needed.
2. Create secrets securely (recommended):
   - `kubectl create secret generic sorenkai-api-secrets \
       --from-literal=MONGODB_URI='mongodb://user:pass@mongo:27017/sorenkai?authSource=admin&retryWrites=false' \
   - `kubectl create secret generic sorenkai-api-google-sa'`'
   (Alternatively, edit `deploy/k8s/secrets.yaml` with placeholders and apply only to non-prod.)
3. Deploy:
   - `kubectl apply -k deploy/k8s`
4. Update the image tag via kustomize when promoting:
   - `kubectl set image deployment/sorenkai-api api=sorenkai/backend:<tag>`

## Secrets management

- No secrets are committed to this repository. The `secrets/` directory is git-ignored.
- Production/staging use external secret stores:
  - Docker Compose: Docker secrets (see `docker-compose.yml`).
  - Kubernetes: K8s Secrets mounted at `/var/run/secrets/google` (see `deploy/k8s/deployment.yaml`).
- Rotation: follow the runbook at `docs/runbooks/`.
- Local development:
  - Set `GOOGLE_APPLICATION_CREDENTIALS` to a directory or file path containing your service account JSON.
  - Do not commit the credential file.

## Data durability (Mongo backups)

- Runbook: see docs/runbooks/mongo-backup-restore.md for Docker Compose and Kubernetes procedures.
- Kubernetes templates: deploy/k8s/backup includes a PersistentVolumeClaim, a daily backup CronJob (02:00 UTC, simple N-day retention), and a restore Job template.
- Recommendation: sync backups to durable object storage and perform quarterly restore drills.

## Runbooks & Incident Response

- Incident Response: see docs/runbooks/incident-response.md for playbooks covering high error rate/latency, Mongo outages, rate limiting, and rollout problems.
- Backups/Restore: docs/runbooks/mongo-backup-restore.md
- credentials rotation: docs/runbooks/
- DSR (export/delete): docs/runbooks/dsr-requests.md

## API lifecycle & versioning

- Versioning strategy: URI-based major versions (e.g., /v1, /v2) for breaking changes. v1 is current and stable.
- Backward-compatible changes: additive fields/endpoints may be introduced within v1; clients should ignore unknown fields.
- Deprecation policy: when functionality is slated for removal, it will be marked `deprecated: true` in the OpenAPI spec and responses MAY include:
  - Deprecation: true
  - Sunset: RFC 8594 date-time of planned removal
  - Link: rel="deprecation" to a migration guide
- Response headers: the server includes `X-API-Version: v1` on all responses for diagnostics.
- Removal timelines: default 90 days between deprecation announcement and removal (unless security/bugfix requires faster action).

See the OpenAPI spec at src/main/resources/openapi/documentation.yaml (info section) for authoritative details.

## Testing

See also Performance and Load testing below for k6-based baseline scripts.

This repository uses a pragmatic testing approach focused on MVP-critical behavior and regression prevention.

- Philosophy: not “every possible test,” but high-value coverage of services, core utilities, and route behaviors. Aim for solid confidence with maintainable tests.
- Structure: test tree mirrors the main source tree.
  - Main: `src/main/kotlin/com/sorenkai/api/...`
  - Tests: `src/test/kotlin/com/sorenkai/api/...`

### What’s covered (so far)
- Unit tests
  - `StoryService`: create flow, trimming/normalization, and rate-limited failure.
  - `CommentService`: happy path, validation errors, story-not-found, rate-limited failure.
  - `ReportService`: happy path and rate-limited failure.
  - `RtdbPostRateLimiter` (local fallback): window/limit increments.
  
- Integration tests
  - Route flows for stories, comments, and reports using in-memory test engine or Testcontainers Mongo to verify pagination.

- Contract tests
  - Snapshot-based checks to ensure responses match the OpenAPI spec for success and error cases.

### How we make services testable
- Introduced a tiny `RateLimiter` interface that the existing RTDB-backed limiters implement. Services depend on the interface, so tests can pass fake implementations.
- Made repository classes `open` and key methods `open` so tests can provide lightweight in-memory fakes by subclassing without pulling extra mocking libs.

### Running tests
- From the repo root:
  - Linux/macOS: `./gradlew test`
  - Windows: `gradlew.bat test`

Gradle will compile and run unit, integration, and contract tests under `src/test/kotlin`. Test resources live in `src/test/resources`.

### Compliance & Data Policy (Step 10)
- Public, user-facing policies (Privacy Policy, Cookie Policy, Community Guidelines, Disclaimer, Copyright) are hosted on the frontend.
- Internal, engineering-facing docs live in this repo to operationalize those promises:
  - Backend Data Policy: `docs/policy/data-policy.md`
  - DSR (export/delete) runbook: `docs/runbooks/dsr-requests.md`
- These give the solo admin practical, copy‑pasteable commands to export or delete a user’s data by UID. Automation endpoints are optional and can be added later.

## Performance & Load testing

- Scripts and docs are under `docs/performance/`.
- Quick start (local):
  - Ensure the server runs with test auth bypass enabled (non-prod only): set TEST_AUTH_ENABLED=true and TEST_AUTH_UID; still include an Authorization header.
  - Stories baseline: `k6 run docs/performance/k6-stories.js`
  - See `docs/performance/README.md` for more.
- Baseline report: `docs/performance/load-test-report.md` (sample numbers and SLI targets).

## Observability to operations

- Metrics: Prometheus metrics are exposed at `/metrics` (Micrometer). Includes `http_server_requests_seconds_*` histograms/counters and custom counters: `stories_created_total`, `comments_created_total`, `reports_created_total`.
- Grafana dashboard: import `docs/observability/dashboards/api-overview.json`.
  - Panels: RPS, 5xx error rate, p95/p99 latency, requests by status, top endpoints, creation counters.
- Prometheus alerts: apply `deploy/monitoring/prometheus-rules.yaml` to your Prometheus Alerting config.
  - Alerts: APIHighErrorRate (warn), APISevereErrorRate (crit), APIHighLatencyP95 (warn), APIHighLatencyP99 (crit), APINoTraffic (warn).

### Kubernetes Prometheus example (ConfigMap)

If you manage Prometheus rules via ConfigMaps, you can create one like:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: sorenkai-api-prometheus-rules
  labels:
    role: alert-rules
    prometheus: k8s
    app: sorenkai-api
data:
  sorenkai-api.rules.yaml: |
    # Paste the contents of deploy/monitoring/prometheus-rules.yaml here
```

Then reference this ConfigMap from your Prometheus deployment/operator according to your setup.


## Security

- Vulnerability disclosure policy: see SECURITY.md
- Threat model: docs/security/threat-model.md
- Final security review checklist: docs/security/checklist.md




## Smoke test a deployed environment
You can run a simple health smoke test against any deployed URL exposing the `/serverhealth` endpoint.

Options:

1) Ad-hoc via GitHub Actions (no secret required)
- Go to the Actions tab → CI → Run workflow.
- Provide the input "smoke_url" with your deployed base URL (e.g., https://staging.example.com) and run.
- The optional "Smoke Test" job will curl `${smoke_url}/serverhealth` and fail if it cannot get a successful response.

2) Automatic on every CI run via secret
- Set repository secret `SMOKE_URL` to your deployed base URL.
- On subsequent CI runs, the "Smoke Test" job will execute after build and test.

Local check (example):
- curl -fsS "http://localhost:8080/serverhealth"

Notes:
- The endpoint `/serverhealth` is unauthenticated and returns a small JSON body when the service is up.
- The CI job targets the `staging` environment and will succeed if it receives a 2xx response.


## Local development with Docker Compose

- Prerequisites: Docker Desktop.
- 
- Start services:
  - Windows PowerShell: `docker compose up --build`
- The API will listen on http://localhost:8080.

Notes:
- Compose sets GOOGLE_APPLICATION_CREDENTIALS=/run/secrets and mounts the service account JSON as a Docker secret.
- MongoDB runs as a local container; the API connects via MONGODB_URI.
- Health check is available at `/serverhealth`.

## API endpoints (v1)

Endpoints

Writings
- GET /v1/writings
  - List published writings. Supports limit and offset. Returns metadata only (no content). Sorted by publishedAt desc.
  - App Check required (Origin/Referer must be in ALLOWED_ORIGINS when enforcement is on).
- GET /v1/writings/{slug}
  - Get a full article, including Markdown content.
  - App Check required.
  - No write endpoints (writings are inserted/administered manually).

Discussions
- GET /v1/discussions[?storyId=...|parentId=...]
  - Get recent top-level posts (kind=POST) with nested comments when requested. Supports pagination (limit/cursor). Optional storyId filter to tie threads to a writing; parentId returns direct replies.
  - App Check required.
- POST /v1/discussions
  - Create a new post/comment. Requires Zitadel auth. Hashtags auto-extracted and audit logged.
- PATCH /v1/discussions/{id}
  - Edit a post/comment. Only the author may edit. Audit logged with field changes.
- DELETE /v1/discussions/{id}
  - Hard delete a post/comment. Only the author may delete. Audit logged.

Audit Logging
- All discussion write operations (CREATE, EDIT, DELETE) are logged to `auditLog` with schema compliance.

Schemas (Mongo $jsonSchema summary)
- Writings (WritingListItem/WritingDetail): title, slug, synopsis (optional), content (single Markdown document), status (DRAFT|PUBLISHED), coverUrl (optional; supports /images/... or https://...), authorId, publishedAt (optional), createdAt, updatedAt, stats { commentCount, viewCount, likeCount }.
- Discussions (POST/COMMENT): body (1..5000), userId, createdAt, updatedAt, optional storyId (ObjectId) to link to a writing, optional parentId (ObjectId) for replies, likes (int, reserved), hashtags (array of strings).
- AuditLog: entityType (DISCUSSION), entityId, action (CREATE|EDIT|DELETE), userId, timestamp, optional changes, optional ip.

Security
- Writings: read-only endpoints protected by App Check (must come from sorenkai.com).
- Discussions: App Check on all; Zitadel auth required for POST, PATCH, DELETE.
- Admin routes (/v1/admin) and /serverhealth are stubbed/admin-only, not exposed to the frontend.

Other
- Likes exist in the discussions schema but are not wired yet.
- Reports flow is stubbed and will be part of admin tools later.
- Excludes any /v1/stories references (replaced by /v1/writings).

## Environment variables

Core configuration:
- PORT: Server port (default 8080)
- MONGODB_URI: Mongo connection string. In Compose, defaults to the local `mongo` service.

Operational:
- ENABLE_JVM_METRICS: Enable Micrometer JVM metrics (default true)
- RATE_POST_WINDOW_SECONDS / RATE_POST_LIMIT: Rate limiting defaults for posts

Testing/CI:
- NO_DOCKER: When `true`, Gradle will skip Testcontainers-based integration tests. Useful in constrained CI runners.

