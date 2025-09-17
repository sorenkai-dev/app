# Still Need: MVP Completion Plan

This document tracks the remaining work to deliver the agreed MVP for the SorenKai backend. It reflects the architecture and code choices already in this repo (Ktor 3, Kotlinx Serialization, Mongo, Firebase Auth, kotlin.time.Instant, in-memory rate limit fallback). Items are ordered by dependency and impact and should be tackled in this order. For a phased delivery view, use the milestones in section 14 (M1→M4).

---

## Production Readiness Plan (Final)

This section captures the final, prioritized plan to take the backend from MVP-complete to production-ready. Each item includes concrete acceptance criteria. We will execute these in order.

1. Production configuration guardrails (Step 1)
   - CORS defaults: In prod, never fall back to allow any origin; require ALLOWED_ORIGINS to be set explicitly. [Implemented: defaults tightened in prod]
   - Swagger/docs: Ensure disabled by default in prod unless SWAGGER_ENABLED=true is set. [Already implemented]
   - Security headers: Maintain strict defaults incl. HSTS in prod or when ENABLE_HSTS=true. [Already implemented]
   - Request size/compression: Enforce sane limits and compression. [Already implemented]
   - Graceful shutdown/timeouts: Configure shutdown grace/timeout via env (SHUTDOWN_GRACE_SECONDS, SHUTDOWN_TIMEOUT_SECONDS). [Implemented]
   - Acceptance: With ENV=prod and no ALLOWED_ORIGINS set, cross-origin requests are blocked; docs are off by default; headers and limits active; graceful shutdown confirmed.

2. Deployment & IaC
  - Container hardening (non-root user, read-only FS where possible), health/readiness probes, minimal base image. ✓
  - Provide Docker/Kubernetes manifests or Terraform snippets; document required env/secrets. ✓
  - Acceptance: Reproducible deployment manifests checked into repo; containers pass probes and run as non-root. ✓
  - Notes: K8s manifests under deploy/k8s include Deployment, Service, ConfigMap, Secrets templates, and Kustomize; README documents apply/create‑secret steps. ✓

3. Secrets management and rotation
   - Use external secret store (e.g., Docker/K8s secrets) for Firebase credentials; document rotation procedure. ✓ (Compose uses Docker secret; K8s mounts Secret at /var/run/secrets/google; app reads GOOGLE_APPLICATION_CREDENTIALS directory)
   - Acceptance: No secrets in repo; rotation runbook; local/dev workflow documented. ✓ (Runbook at docs/runbooks/firebase-credentials-rotation.md; README updated)

4. Observability to operations
   - Define SLOs and alerts (availability, latency, error rates). Provide dashboards for key metrics. ✓
   - Acceptance: Grafana dashboards and alert rules committed or documented with importable JSON. ✓
   - Artifacts: Grafana dashboard at docs/observability/dashboards/api-overview.json; Prometheus alert rules at deploy/monitoring/prometheus-rules.yaml. ✓

5. Logging/PII hygiene
   - Ensure no sensitive data in logs; add redaction for Authorization and PII. ✓ (Logback sanitizer masks Authorization Bearer tokens and emails; applied via custom converter)
   - Acceptance: Sanitizer in logging; unit test verifying headers are redacted. ✓ (SanitizerTest covers Authorization header and JSON cases)

6. Security scanning and dependency updates
   - Enable Dependabot/Renovate; add SAST/Container scan in CI. ✓ (Dependabot configured for Gradle and GitHub Actions at .github/dependabot.yml; CI security workflow added)
   - Acceptance: CI job fails on critical CVEs; automated PRs for updates. ✓ (Trivy container scan fails on CRITICAL; Semgrep SAST runs on PRs/push; weekly scheduled scans)

7. Data durability
   - Backups and restore procedure for Mongo; retention policy documented. ✓ (Runbook at docs/runbooks/mongo-backup-restore.md; K8s CronJob + PVC + restore Job under deploy/k8s/backup)
   - Acceptance: Successful restore drill documented; scripts or operator notes included. ✓

8. Performance and load testing
   - Baseline latency/throughput; size indexes as needed; tune page sizes. ✓ (pageSize capped to 100 on list endpoints)
   - Acceptance: Load test report checked in; SLIs within targets at expected load. ✓ (k6 scripts under docs/performance; baseline report at docs/performance/load-test-report.md)

9. Zero-downtime deploys & idempotency
   - Ensure idempotent POSTs where possible; graceful shutdown; rolling updates.
   - Acceptance: Deployment can roll with no failed requests in canary test. ✓ (K8s rollingUpdate maxUnavailable=0, lifecycle preStop drain, terminationGracePeriodSeconds set; app graceful shutdown already implemented)
   - Deferred: Idempotency-Key persistence for POST endpoints (return 200 on safe retries) — rationale:
     - Current state: POST retries are functionally safe today because domain-level uniqueness constraints already prevent duplicate writes (e.g., stories unique on (createdBy, url)). A retry of the same payload may return 409 CONFLICT instead of 200/201, but no duplicate resource is created.
     - Why defer: Implementing full idempotency persistence adds non-trivial complexity and infra:
       - Contract: standardize an Idempotency-Key header and document semantics (201 on first, 200 on subsequent identical requests; 422 on key-reuse with different payload).
       - Storage: a consistent, low-latency store keyed by (uid, route, key) with TTL (e.g., Redis preferred; Mongo with TTL index is viable but less ideal for high-QPS hot keys).
       - Correctness: handle races (first-writer wins), store response snapshots, validate payload hash per key, and implement cleanup/observability.
       - Ops & tests: additional monitoring, alerting on key-collision anomalies, and broader test matrix (concurrency, TTL expiry, crash-recovery).
     - Impact vs priority: Returning 409 on duplicate POST is acceptable for MVP; clients can treat 409 as a successful prior write and proceed (optionally GET the resource). Given higher-impact production tasks (security, deploys, backups, tests) we prioritized those.
     - When to implement: if we observe high client retry rates due to flaky networks/mobile, add payment-like semantics, or integrate partners requiring strict idempotency semantics.
     - Proposed design (future):
       - Header: Idempotency-Key (opaque; max ~255 chars). Scope by uid + route.
       - Persistence: collection/table with unique index on (uid, route, key), fields: payloadHash, statusCode, responseBody, createdAt. TTL index ~24–72h.
       - Behavior: first request processes normally (usually 201) and stores snapshot; subsequent with same key + identical payload returns stored response with 200; if payload differs → 422 with guidance.
       - Safety: guard size of stored snapshots; redact PII in logs; include metrics (idempo_hits_total, idempo_mismatch_total).

10. Compliance & data policy
   - Data retention, deletion, and export policy documented; implement endpoints if needed. ✓
   - Acceptance: Policy document exists; gaps tracked as issues. ✓ (See docs/policy/data-policy.md and docs/runbooks/dsr-requests.md; public-facing policies are on the frontend)

11. Runbooks and incident response
   - On-call playbooks for common failures; escalation paths. ✓
   - Acceptance: Runbooks committed under /docs/runbooks. ✓ (Incident Response runbook added at docs/runbooks/incident-response.md; existing backup/DSR/rotation runbooks referenced)

12. API lifecycle/versioning
   - Define deprecation policy and versioning approach; ensure OpenAPI reflects it.
   - Acceptance: Policy in README/OpenAPI; headers for deprecation where needed.

13. CI/CD enhancements
   - Add environments, caching, smoke tests post-deploy.
   - Acceptance: Pipeline promotes artifacts through stages; smoke tests gate prod.

14. Final security review
   - Threat model and checklist; resolve high/critical findings.
   - Acceptance: Review doc signed off; all critical issues closed.

---

1. Authentication and RBAC
   - Enforce Firebase ID token verification on protected routes (POST /v1/stories already requires it). ✓
   - Add role/claims model: basic roles = [user, mod, admin]. ✓
   - Gate moderation/report endpoints to mod+ only. → Infra ready (requireRole helper); will apply when endpoints are added.
   - Utility to extract optional user in GETs (for future personalized responses). ✓

2. Stories domain: hardening and pagination
   - Validation: title/url/summary/tags – expand current RequestValidation with limits and URL normalization. ✓ (limits enforced; normalization via trim + scheme check)
   - Uniqueness: keep Mongo unique index on (createdBy, url); ensure createdAt index for sorting. ✓ (added compound index on createdAt,id desc)
   - Pagination: implement cursor-based pagination for GET /v1/stories using createdAt,id descending. ✓
     - DTOs: Cursor, Page<T> already exist; wire them into StoriesRoutes + StoryRepository. ✓
     - Query: find() sorted by {createdAt: -1, id: -1}, limit pageSize+1 to compute nextCursor. ✓

3. Comments
   - Model: Comment(id, storyId, body, createdAt, createdBy). ✓
   - Endpoints:
     - POST /v1/stories/{id}/comments (auth required, rate limited like posts but separate bucket). ✓
     - GET /v1/stories/{id}/comments with cursor pagination. ✓
   - Indexes: {storyId:1, createdAt:-1, id:-1}. ✓

4. Reports & Moderation
   - Model: Report(id, targetType: [story|comment], targetId, reason, createdAt, createdBy, status: [open|closed]). ✓
   - Endpoints:
     - POST /v1/reports (auth required) ✓
     - GET /v1/mod/reports?status=open (mod only, paginated) ✓
     - POST /v1/mod/reports/{id}/close (mod only) ✓
   - Admin tooling: simple mod close/open, future audit log optional. (basic close implemented) ✓

5. Rate limiting
   - Current: in-memory fixed-window limiter for POST /v1/stories. ✓ (now refactored behind RTDB-aware core)
   - Add Firebase RTDB-backed limiter for distributed enforcement; fall back to in-memory if not configured. ✓ (via RtdbLimiterCore)
   - Separate buckets: post:stories, post:comments, report:create. ✓ (Post, Comment, Report limiters)
   - Config via env: RATE_POST_WINDOW_SECONDS, RATE_POST_LIMIT, RATE_COMMENT_WINDOW_SECONDS, RATE_COMMENT_LIMIT, RATE_REPORT_WINDOW_SECONDS, RATE_REPORT_LIMIT, FIREBASE_RTDB_URL. ✓

6. Error handling and responses
   - Centralize ApiException mapping in StatusPages into ApiErrorResponse with ErrorCode and details. ✓
   - Map RequestValidation errors to 400 with field messages. ✓
   - Map Mongo duplicate key to 409 CONFLICT (duplicate story URL per author). ✓

7. Observability
   - Metrics: Micrometer Prometheus already included; expose /metrics and add counters/timers for key routes. ✓ (Micrometer installed, /metrics exposed, custom counters: stories_created_total, comments_created_total, reports_created_total)
   - Structured logging: include callId, uid (when present), route, status, duration. ✓ (CallLogging MDC for method/path/status; uid captured via MDC in auth; logback pattern outputs reqId, uid, method, path, status; duration included in CallLogging message)
   - Basic request IDs via Ktor CallId and logback pattern configured. ✓

8. OpenAPI/Docs
   - Maintain OpenAPI spec at src/main/resources/openapi/documentation.yaml. ✓
   - Ensure schemas for Story, StoryCreateRequest, Page, Cursor, Comment, Report, ApiErrorResponse. ✓ (Page* concrete types with nextCursor; Cursor is opaque string)
   - Serve Swagger UI in non-prod or behind auth. ✓ (served at /docs and /openapi, auto-enabled unless ENV=prod; override with SWAGGER_ENABLED)

9. Configuration & Secrets
   - Environment variables: ✓
     - MONGODB_URI
     - GOOGLE_APPLICATION_CREDENTIALS (path or directory) and FIREBASE_PROJECT_ID
     - FIREBASE_RTDB_URL (or FIREBASE_DATABASE_URL as fallback) for RTDB-backed rate limiter
     - RATE_* for rate limits
     - PORT
   - Docker/Docker Compose: ✓
     - api service wires all envs; uses Docker secret for Firebase SA at /run/secrets
     - Healthcheck uses curl to hit /healthz

10. Testing
   - Intent: Not “write every possible test,” but establish a solid, maintainable test suite that covers critical paths for the MVP and prevents regressions. Prioritize behavior that affects correctness, security, and availability. Aim for high confidence with pragmatic scope.
   - Unit: services (StoryService, CommentService, ReportService), rate limiter core (RtdbLimiterCore in local mode), validators (RequestValidation rules). Target ≥80% line coverage for services.
   - Integration: Ktor routes via TestApplication/TestHost with in-memory engine; mock/bypass FirebaseAuth token verification; run against a test Mongo database to exercise repositories and verify cursor pagination (implemented via Testcontainers for Mongo with end-to-end paging tests for stories, comments, and moderator reports). ✓
   - Contract: snapshot-based checks to ensure actual responses match the OpenAPI spec shapes for happy-path and error cases. ✓ (implemented via snapshot-based ContractTests and snapshots under src/test/resources/snapshots)
   - CI: GitHub Actions workflow added to build and run tests on PRs/pushes; uploads test report artifacts. ✓

11. Security checklist
   - CORS: restrict origins per env. ✓ (CORS configured with ALLOWED_ORIGINS; defaults to anyHost only if not set)
   - Headers: security headers via DefaultHeaders; disable server version exposure. ✓ (X-Frame-Options=DENY, X-Content-Type-Options=nosniff, Referrer-Policy=no-referrer, Permissions-Policy minimal, HSTS enabled in prod or when ENABLE_HSTS=true)
   - Input size limits; compression settings sane. ✓ (custom RequestSizeGuard enforces MAX_UPLOAD_MB; gzip/deflate enabled)
   - Auth: ensure all mutation endpoints require valid token; enforce RBAC on mod endpoints. ✓ (POSTs require auth; requireRole() protects mod routes)
   - Test auth bypass: only active when ENV=test or TEST_AUTH_ENABLED=true (and never in prod); still requires an Authorization header. ✓

12. Performance and scalability
   - Indexes verified: stories(createdAt, id), stories(createdBy, url unique).
   - Add comments and reports indexes.
   - Page sizes capped; protect against deep pagination via cursors.

13. Migration/Backfill
   - Existing stories: verify id/createdAt using current serializers.
   - If old fields remain (authorUid, _id), add migration or compatibility reading if necessary.

14. Minimal milestones and order of delivery
   - M1: Stories hardening + pagination + error mapping + observability basics.
   - M2: Comments CRUD + rate limits + indexes.
   - M3: Reports + mod endpoints + RBAC.
   - M4: OpenAPI completeness + testing + CI.

Notes and agreements
- Time model: kotlin.time.Instant is used consistently server-side; JSON via kotlinx-serialization. ✓
- JSON: Ktor ContentNegotiation with Kotlinx JSON is configured. ✓
- Duplicate RequestValidation installation removed; a single module handles StoryCreateRequest. ✓
- Mongo indexes: (createdBy, url) unique is in place; add others as per features above.
