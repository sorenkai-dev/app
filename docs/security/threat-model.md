# Threat Model (SorenKai API)

This document summarizes key assets, trust boundaries, data flows, and threats for the SorenKai backend API, along with mitigations and current status. It is a living document and should be updated alongside major changes.

Last updated: 2025-09-17

## 1) Scope and assumptions

- Components in scope:
  - API server (Ktor 3, Kotlin), exposed over HTTPS behind a reverse proxy/ingress.
  - MongoDB used for persistence (stories, comments, reports) — managed service or self‑hosted.
  - Firebase Admin SDK used for verifying ID tokens (authn) and optional RTDB for distributed rate limiting.
  - CI/CD pipelines and container images used to build and deploy the API.
- Out of scope (documented for awareness):
  - Frontend/web clients and their storage of tokens.
  - Third-party observability backends (Prometheus, Grafana) beyond the API’s exposure of /metrics.
- Assumptions:
  - TLS termination is handled by a managed ingress or reverse proxy; HSTS is enabled at the API in prod.
  - Secrets are provided via Docker/K8s secrets; no secrets are baked into images.

## 2) Assets

- User data in MongoDB (stories, comments, reports).
- Authentication tokens (Firebase ID Tokens) in transit only.
- Admin/moderator capabilities (RBAC‑protected).
- Build artifacts and container images.

## 3) Trust boundaries and data flows

- External Clients → API (HTTPS, CORS controls) → MongoDB
- External Clients → API (HTTPS) → Firebase Admin (token verification)
- API (optional) → Firebase RTDB (rate limiting state)
- CI/CD → Container Registry → Kubernetes/Docker Runtime

## 4) Threats and mitigations (STRIDE overview)

- Spoofing (identity)
  - Threat: Forged/invalid JWTs.
  - Mitigation: Firebase Admin token verification on mutation endpoints; optional test auth bypass disabled in prod and gated by env flags; RBAC enforcement on moderator/admin routes.
  - Status: Implemented.

- Tampering (data)
  - Threat: Unauthorized writes, replayed POSTs.
  - Mitigation: Auth + RBAC; unique indexes prevent duplicate story URLs; rate limits per user; idempotency persistence deferred (see StillNeed.md) but duplicate writes are prevented by constraints.
  - Status: Implemented; idempotency store deferred with rationale.

- Repudiation
  - Threat: Lack of traceability.
  - Mitigation: Structured logging with request IDs and optional UID in MDC after successful auth; logs sanitized to avoid credential/PII leakage.
  - Status: Implemented.

- Information disclosure
  - Threat: Sensitive data leakage via logs or overly broad CORS.
  - Mitigation: Log sanitizer redacts Authorization and emails; CORS strict in prod (requires ALLOWED_ORIGINS); security headers; Swagger/docs disabled by default in prod.
  - Status: Implemented.

- Denial of Service
  - Threat: Excessive load, oversized requests, huge page sizes.
  - Mitigation: Server‑side pageSize caps (≤100); request size guard (MAX_UPLOAD_MB); gzip/deflate; rate limiting per bucket with RTDB distributed option and in-memory fallback; graceful shutdown and rolling deploys.
  - Status: Implemented.

- Elevation of privilege
  - Threat: Accessing moderator/admin endpoints without roles.
  - Mitigation: requireRole(Role.MOD/ADMIN) checks; test bypass never active in prod; OpenAPI documents security.
  - Status: Implemented.

## 5) Supply chain and CI/CD

- Risks: vulnerable dependencies, compromised base images, malicious PRs.
- Mitigations: Dependabot for Gradle and GH Actions; CI builds and runs tests; Security workflow builds container and scans with Trivy (fail on CRITICAL); Semgrep SAST with SARIF upload.
- Status: Implemented.

## 6) Secrets management

- Risks: secret leakage, accidental commit.
- Mitigations: secrets/ is git‑ignored; Docker/K8s secrets used; rotation runbook; Firebase creds read from mounted path or env; README documents practices.
- Status: Implemented.

## 7) Residual risks / future work

- Idempotency-Key persistence for POST retries — deferred; revisit if clients exhibit high retry rates.
- Add Content-Security-Policy when serving any HTML beyond Swagger (currently minimal UI in non‑prod).
- Consider WAF/rate limiting at the edge for coarse‑grained protection.

## 8) References

- StillNeed.md (Production Readiness Plan)
- README.md (Security, Versioning, Observability)
- docs/runbooks/* (Incidents, Backups, Credentials rotation)
