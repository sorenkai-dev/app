# Security Review Checklist

Use this checklist to perform and record the final security review prior to production release. Link evidence and mark pass/fail. Update as the system evolves.

Last updated: 2025-09-17

Legend: [✓] pass, [!] fail, [—] N/A

## Application
- [✓] Authentication: Firebase ID token verification on all mutation endpoints. Evidence: Auth.kt, routes use extractUidOrUnauthorized/requireRole.
- [✓] RBAC: Moderator/Admin routes require appropriate roles. Evidence: requireRole(Role.MOD/ADMIN) in ReportsRoutes/AdminRoutes.
- [✓] Input validation: RequestValidation rules and explicit checks for stories/comments/reports. Evidence: Validatiion.kt and route/service exceptions -> StatusPages mapping.
- [✓] Error handling: Centralized StatusPages returns structured ApiErrorResponse; hides internal details. Evidence: routes/Routing.kt.
- [✓] Security headers: X-Frame-Options, X-Content-Type-Options, Referrer-Policy, HSTS (prod). Evidence: config/Http.kt.
- [✓] CORS: Restricted in prod to ALLOWED_ORIGINS; no anyHost fallback. Evidence: config/Http.kt.
- [✓] Request size limits: MAX_UPLOAD_MB guard. Evidence: config/Http.kt.
- [✓] Pagination caps: pageSize coerced to 1..100 on list endpoints. Evidence: StoriesRoutes/ReportsRoutes.
- [✓] Rate limiting: Per-user buckets for posts, comments, reports; RTDB distributed option with local fallback. Evidence: rate/*.
- [✓] Logging hygiene: Log sanitizer redacts Authorization and emails. Evidence: logging/Sanitizer.kt and logback.xml converter. Tests: SanitizerTest.
- [✓] Observability: Metrics at /metrics; dashboards + alert rules. Evidence: Monitoring.kt, docs/observability, deploy/monitoring.
- [✓] Docs exposure: OpenAPI/Swagger disabled by default in prod unless explicitly enabled. Evidence: Routing.kt (SWAGGER_ENABLED flag handling).
- [✓] API versioning header: X-API-Version fixed to v1. Evidence: Http.kt DefaultHeaders.

## Data & Persistence
- [✓] Mongo indexes: Uniqueness and pagination indexes set; ensured on startup. Evidence: StoryRepository/CommentRepository/ReportRepository + initRepos().
- [✓] Backups & restore: Runbook and K8s CronJob provided; retention guidance. Evidence: docs/runbooks/mongo-backup-restore.md, deploy/k8s/backup/*.
- [✓] Data policy & DSR: Internal policy with export/delete runbook. Evidence: docs/policy/data-policy.md, docs/runbooks/dsr-requests.md.

## Secrets
- [✓] No secrets in repo: secrets/ git-ignored; audit performed. Evidence: .gitignore, prior VCS audit.
- [✓] Provisioning: Docker secrets / K8s Secrets; service account mount path; rotation runbook. Evidence: docker-compose.yml, deploy/k8s/deployment.yaml, docs/runbooks/firebase-credentials-rotation.md.

## Supply chain & CI
- [✓] Dependency updates: Dependabot for Gradle and GitHub Actions. Evidence: .github/dependabot.yml.
- [✓] CI build & tests: CI workflow builds and runs tests with reports. Evidence: .github/workflows/ci.yml.
- [✓] Container scan: Trivy scans built image; fails on CRITICAL. Evidence: .github/workflows/security.yml.
- [✓] SAST: Semgrep runs; SARIF uploaded when present. Evidence: .github/workflows/security.yml.

## Deployment & Runtime
- [✓] Containers run as non-root; read-only root filesystem; dropped capabilities. Evidence: Dockerfile (non-root) and deploy/k8s/deployment.yaml securityContext.
- [✓] Health/Readiness: /healthz probes configured. Evidence: deploy/k8s/deployment.yaml.
- [✓] Graceful shutdown: Server stop with grace/timeout; K8s preStop+terminationGracePeriod. Evidence: App.kt, deploy/k8s/deployment.yaml.
- [✓] Zero-downtime rollout: RollingUpdate with maxUnavailable=0, maxSurge=1. Evidence: deploy/k8s/deployment.yaml.

## Residual Risks / Deferred Items
- [i] Idempotency-Key persistence for POSTs deferred; uniqueness constraints prevent duplicate writes but may return 409 on retries. See StillNeed.md rationale.
- [i] Consider CSP if serving richer HTML content; Swagger UI is minimal and non-prod by default.
- [i] Consider edge-level rate limiting/WAF for coarse-grained protection.

## Review Sign-off
- Reviewer: ____________________  Date: ___________
- Findings (high/critical): ________________________________________________
- Actions taken: ___________________________________________________________
- Follow-ups created (issue links): ________________________________________
