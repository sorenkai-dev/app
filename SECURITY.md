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
