# Data Subject Requests (DSR) Runbook

This runbook is for the solo admin to fulfill user data requests: export my data, and delete my data. It operationalizes the internal Backend Data Policy and the public Privacy Policy.

Assumptions:
- You have access to the MongoDB instance for the target environment.
- You know the Firebase UID of the user making the request.
- Production changes should be deliberate and verified; practice in a staging environment first.

## 1) Export a user’s data (by UID)

Scope: stories, comments, and reports authored by the UID.

Steps:
1. Set an environment variable with the UID:
   - PowerShell (Windows): `$env:UID = "<the-firebase-uid>"`
   - bash (Linux/macOS): `export UID="<the-firebase-uid>"`
2. Connect to Mongo for the environment (replace URI):
   - `mongosh "<MONGODB_URI>"`
3. In mongosh, run:
   ```js
   const uid = process.env.UID;
   const stories = db.stories.find({ createdBy: uid }).toArray();
   const comments = db.comments.find({ createdBy: uid }).toArray();
   const reports = db.reports.find({ createdBy: uid }).toArray();
   const bundle = { uid, stories, comments, reports, exportedAt: new Date().toISOString() };
   JSON.stringify(bundle);
   ```
4. Copy the JSON output to a secure file (e.g., `export-<uid>-YYYYMMDD.json`) and provide to the requester via your approved channel.

Notes:
- Do not include logs/metrics in the export.
- If bundle is large, consider writing to a file via `mongosh` shell redirection or use `mongoexport` per collection.

## 2) Delete a user’s data (by UID)

Default strategy (as per Backend Data Policy):
- Stories/Comments: hard delete documents authored by the UID.
- Reports: delete reports created by the UID (or redact identifying fields if you choose to preserve moderation history). The default here is hard delete of the reporter’s own reports.

Steps:
1. Practice in staging with a test UID if possible.
2. Confirm the requester’s UID (e.g., they can sign in and tell you their UID, or you match via a verified channel).
3. Take a snapshot export first (Section 1) and retain it securely for the minimal time necessary to fulfill the request (if permitted by your policy) or discard immediately if not allowed.
4. Connect to Mongo:
   - `mongosh "<MONGODB_URI>"`
5. In mongosh, run (review counts before commit):
   ```js
   const uid = "<the-firebase-uid>";
   // Preview counts
   db.stories.countDocuments({ createdBy: uid });
   db.comments.countDocuments({ createdBy: uid });
   db.reports.countDocuments({ createdBy: uid });

   // Perform deletions
   const s = db.stories.deleteMany({ createdBy: uid });
   const c = db.comments.deleteMany({ createdBy: uid });
   const r = db.reports.deleteMany({ createdBy: uid });
   ({ storiesDeleted: s.deletedCount, commentsDeleted: c.deletedCount, reportsDeleted: r.deletedCount })
   ```
6. Verify: run the count queries again—they should return 0 for the UID.
7. Document the request: UID, date/time, who approved/performed, what was deleted, and any references to ticket/email.

Caveats:
- If you later prefer soft-deleting stories/comments (to preserve discussion threads), implement a `deleted: true` flag in the models and update routes to exclude deleted content; then update this runbook accordingly.
- Reports deletion policy can be changed to redaction instead (e.g., keep the report but set `createdBy: null`), if you want to retain moderation evidence.

## 3) Safety and rollback

- Always run exports before deletion where allowed.
- For rollback after accidental delete, you would need to restore from backups (see `docs/runbooks/mongo-backup-restore.md`). Consider a partial restore to a temporary DB, then re-insert documents.

## 4) FAQ

- Q: Do I need to touch logs?
  - A: No; logs are sanitized and aren’t part of the user export.
- Q: What about metrics?
  - A: Metrics are aggregated and not tied to individual UIDs; not exported.
- Q: How do I find the UID?
  - A: From Firebase Authentication admin console or from an authenticated request the user performs.
