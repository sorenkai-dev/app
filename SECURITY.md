# Security Policy

## Supported Versions

The backend follows semantic versioning. Only the latest minor release of the current major version (v1.x) receives security fixes. Older unsupported versions should be upgraded to the latest release.

## Reporting a Vulnerability

If you believe you’ve found a security vulnerability, please do not open a public issue. Instead, contact the maintainer directly:

- Email: security@sorenkai.example (replace with a real contact)

Please include:
- A description of the issue and potential impact
- Steps to reproduce (if possible)
- Any relevant logs or PoCs (do not include secrets)

We aim to acknowledge reports within 72 hours and provide a remediation plan or timeline as soon as practical. If the issue is confirmed, we will work on a fix and coordinate disclosure timing with you.

## Disclosure

We prefer responsible disclosure. Once a fix is available, we will publish release notes describing the impact and mitigation steps. If there are workarounds, they will be documented.

## Hardening References

- Threat model: docs/security/threat-model.md
- Security review checklist: docs/security/checklist.md
- Secrets rotation runbook: docs/runbooks/firebase-credentials-rotation.md
- Incident response runbook: docs/runbooks/incident-response.md

## Runtime security controls (as of v1)

- Writings page (/v1/stories):
  - Protected by Firebase App Check. Clients must send `X-Firebase-AppCheck` when `APP_CHECK_ENFORCE=true`.
  - Defense-in-depth: server checks `Origin` or `Referer` against the `ALLOWED_ORIGINS` allowlist. A mismatch returns HTTP 403.
  - In development, compose defaults to `APP_CHECK_ENFORCE=false` to simplify local testing.
- Community discussions (/v1/discussions and related):
  - Require Firebase user authentication (Authorization: Bearer <ID token>). Tokens are verified using Firebase Admin with revocation checks enabled.
  - Deletion is scoped: users may delete only their own posts/comments.
- Audit logging:
  - CREATE and DELETE actions for Stories and Discussions write a record to the `auditLog` collection. Logging is best-effort; failures are surfaced to logs and do not expose sensitive data.

## Secrets and credentials

- The backend requires a Firebase service account credential and the Firebase project ID.
- In Docker/Kubernetes, credentials are supplied via environment and mounted secrets. The application reads `GOOGLE_APPLICATION_CREDENTIALS` (file or directory) and will locate a JSON credential within.
- Do not commit secrets to the repository. Use Docker secrets or your orchestrator’s secret manager.
