# Backend Data Policy (Internal)

This is an internal engineering document describing how the SorenKai backend collects, stores, retains, exports, and deletes user data in order to fulfill the public-facing Privacy Policy. It is not a substitute for the public policy; instead it shows how we technically keep those promises.

Public policy reference: The user-facing Privacy Policy, Cookie Policy, Community Guidelines, Disclaimer, and Copyright Policy are hosted with the frontend and remain the source of truth for communications with users.

## 1) Data inventory

- Stories (collection: `stories`)
  - Fields: id, title, url, summary, tags[], createdAt, createdBy (UID)
  - PII: `createdBy` is personal data; free text (title/summary/tags) may contain user-provided PII inadvertently.
- Comments (collection: `comments`)
  - Fields: id, storyId, body, createdAt, createdBy (UID)
  - PII: `createdBy` is personal data; `body` may contain PII.
- Reports (collection: `reports`)
  - Fields: id, targetType, targetId, reason, status, createdAt, createdBy (UID)
  - PII: `createdBy` is personal data; `reason` may contain PII.
- Logs/Metrics (not in Mongo)
  - We avoid logging PII; Authorization tokens and emails are redacted by the log sanitizer.

Storage: MongoDB (connection via `MONGODB_URI`).

Regions/Hosting: per environment; see deployment/infra docs. If cross-border constraints arise, revisit retention and access.

## 2) Retention guidance

- Stories & Comments: retained indefinitely by default as user-generated content, unless user requests deletion, or policy requires cleanup after prolonged inactivity. If you need a specific retention (e.g., delete after N years), change this doc and open an issue to implement automation (TTL/archival/cleanup job).
- Reports: retain for 12 months after closure to support moderation/audit. Adjust as needed; implementation may be manual for now (see runbook).
- Logs: application logs are for ops and troubleshooting only. Recommended retention: 30 days (in your logging system). Logs contain no bearer tokens or emails (sanitized). Do not export logs for DSR.

## 3) Deletion strategy

- Stories & Comments: use hard delete for user DSR by default (remove documents). If you prefer soft delete to preserve discussion threads, change strategy here and add a `deleted` flag implementation.
- Reports: do not remove historical moderation records by default. Redact reporter UID on DSR, or delete the reporter’s own reports if policy requires. This doc defaults to deleting the user’s own reports.

If policy changes, update this section and the DSR runbook.

## 4) Export scope (DSR)

When exporting a user’s data by UID:
- Include: all Stories and Comments authored by that UID; Reports they created.
- Exclude: other users’ content; internal metrics/logs.

## 5) Operationalization

- Manual procedures and sample commands/scripts live in `docs/runbooks/dsr-requests.md`.
- Optional automation endpoints can be added later (admin-only):
  - GET /v1/admin/users/{uid}/export
  - POST /v1/admin/users/{uid}/delete

## 6) Compliance links

- Public Privacy Policy: hosted in frontend (link there).
- Cookie Policy, Community Guidelines, Disclaimer, Copyright: hosted in frontend.

## 7) Ownership and updates

- Owner: Backend maintainers (you, as the current solo admin).
- Update this doc whenever data models change or retention policy is revised.
