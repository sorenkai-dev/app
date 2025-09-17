# MongoDB Backups and Restore Runbook

This runbook documents how to back up and restore the SorenKai MongoDB database for both Docker Compose and Kubernetes deployments. It also defines retention guidance and how to run periodic backups in Kubernetes.

Key principles:
- Backups must be automated and routinely verified (restore drill).
- Store backups in a durable location (object storage or snapshot-capable PV). Local disks are acceptable for dev only.
- Encrypt backups if they leave the cluster/host.

Contents:
- 1) Manual backup and restore (Docker Compose)
- 2) Manual backup and restore (Kubernetes)
- 3) Scheduled backups in Kubernetes (CronJob)
- 4) Retention policy
- 5) Restore drill procedure

---

## 1) Docker Compose: Manual backup and restore

Prerequisites:
- The `mongo` service is running from `docker-compose.yml`.

Backup (mongodump to host directory `./backups`):

```bash
# Create timestamped directory and run mongodump inside a disposable mongo container
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p backups/$TS

docker run --rm \
  --network sorenkai_default \
  -v "${PWD}/backups/$TS:/dump" \
  mongo:7.0 bash -lc "mongodump --uri='${MONGODB_URI:-mongodb://sorenkai:sorenkai@mongo:27017/sorenkai?authSource=admin&retryWrites=false}' --out=/dump"

# Optionally compress
cd backups && tar -czf ${TS}.tar.gz ${TS} && rm -rf ${TS}
```

Restore (from a `backups/<TS>` folder or `.tar.gz`):

```bash
# If you have a tarball
TS=<your-timestamp>
mkdir -p /tmp/restore && tar -xzf backups/${TS}.tar.gz -C /tmp/restore

# Run mongorestore
docker run --rm \
  --network sorenkai_default \
  -v "/tmp/restore/${TS}:/restore" \
  mongo:7.0 bash -lc "mongorestore --uri='${MONGODB_URI:-mongodb://sorenkai:sorenkai@mongo:27017/sorenkai?authSource=admin&retryWrites=false}' --drop /restore"
```

Notes:
- `--drop` will replace existing data in target collections; omit it to merge.
- Ensure the compose network name matches your environment (may differ from `sorenkai_default`).

---

## 2) Kubernetes: Manual backup and restore

We provide templates under `deploy/k8s/backup/`. Apply them to your namespace as needed.

Ad-hoc backup Job (writes to a backup PVC):

```bash
# Create (or ensure) the backup PVC exists
kubectl apply -f deploy/k8s/backup/mongo-backup-pvc.yaml

# Run a one-off backup job with the same container logic as the CronJob
kubectl create job --from=cronjob/mongo-backup-daily mongo-backup-manual

# When complete, the dump(s) are in the PVC at /backups inside the job pod
```

Ad-hoc restore Job (from a specific timestamped dump directory on the PVC):

```bash
# Edit the job template to set RESTORE_TS to your desired timestamp directory name
kubectl apply -f deploy/k8s/backup/mongo-restore-job.yaml

# Wait for completion
kubectl wait --for=condition=complete job/mongo-restore
```

Alternative (no PVC): Exec mongodump directly from the Mongo primary pod (stateful Mongo setup) or use an object storage-based approach with enterprise tooling.

---

## 3) Kubernetes: Scheduled backups (CronJob)

A CronJob template is provided at `deploy/k8s/backup/mongo-backup-cronjob.yaml`. It:
- Runs daily at 02:00 UTC by default.
- Performs `mongodump` to `/backups/<YYYYMMDD-HHMMSS>` on a mounted PVC.
- Prunes local backup directories older than N days (configurable via `RETENTION_DAYS`, default 7).

Apply:

```bash
kubectl apply -f deploy/k8s/backup/mongo-backup-pvc.yaml
kubectl apply -f deploy/k8s/backup/mongo-backup-cronjob.yaml
```

Retrieve backups:
- Use `kubectl cp` from a temporary pod mounting the same PVC, or mount the PVC to a file transfer job.

---

## 4) Retention policy

Recommended defaults (adjust per compliance):
- Daily backups retained for 7 days.
- Weekly backups (e.g., Sunday) retained for 4 weeks.
- Monthly backups retained for 6 months.

The provided CronJob only enforces a simple N-day local retention on the PVC. For tiered retention and durability, sync the backup directory to object storage (e.g., S3/GCS/Azure Blob) via an additional job or sidecar.

---

## 5) Restore drill procedure (acceptance)

At least once per quarter, perform a restore drill:
1. Choose a recent backup timestamp `TS`.
2. In a non-prod namespace, deploy a Mongo instance and apply the restore job with `RESTORE_TS=TS`.
3. Point a copy of the API (with read-only configs) at the restored DB and run basic smoke tests (health, list stories, etc.).
4. Document the drill date, TS used, duration, and results.

Verification:
- After restore, ensure indexes exist (our repositories create indexes at startup via `initRepos()` in the API). If restoring only DB, run a one-time job or start the API to re-ensure indexes.

Security:
- Treat backups as sensitive; avoid storing tokens/PII in world-readable locations; consider encrypting at rest and in transit.
