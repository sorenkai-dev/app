# Firebase Service Account Credentials: Rotation Runbook

This runbook documents how to safely rotate the Firebase Admin SDK service account credentials used by the SorenKai API.

Important principles:
- Never commit secrets to the repository. The `secrets/` directory is ignored by Git.
- In containers and Kubernetes, mount credentials via secret mechanisms, not bind mounts from the image.
- Prefer rotating by adding a new key, updating the runtime, verifying, then revoking the old key.

Prerequisites:
- Project owner or editor access to the Firebase/GCP project.
- kubectl access to the relevant cluster/namespace (for K8s deploys), or Docker access (for compose).

## 1) Create a new service account key
1. Go to Google Cloud Console → IAM & Admin → Service Accounts.
2. Select the service account used by the backend (e.g., `firebase-admin-sdk`).
3. Keys → Add Key → Create New Key → JSON → Download.
4. Store it securely on your workstation; treat it as sensitive.

Optionally, consider creating a separate, least-privilege service account just for this backend if not already done.

## 2) Update runtime secrets

### Kubernetes
1. Base64-encode is not required when using `--from-file`.
2. Replace the existing secret content with the new key file:
   kubectl create secret generic sorenkai-api-google-sa \
     --from-file=firebase-service-account.json=/path/to/new-key.json \
     --dry-run=client -o yaml | kubectl apply -f -
3. Confirm secret updated:
   kubectl get secret sorenkai-api-google-sa -o yaml | grep -A2 data:
4. Ensure the Deployment mounts this secret at `/var/run/secrets/google` (already configured).
5. Trigger a rolling restart of the Deployment (if not automatic via change detection):
   kubectl rollout restart deployment/sorenkai-api
6. Watch rollout:
   kubectl rollout status deployment/sorenkai-api

### Docker Compose (local/staging)
1. Save the new JSON to a secure location on the host.
2. Update the Docker secret reference if needed (compose file expects `secrets/firebase_sa`):
   - secrets/firebase_sa.file should point to the new JSON file path.
3. Recreate the service:
   docker compose up -d --force-recreate --no-deps api
4. Verify logs show successful Firebase initialization.

## 3) Verify
- Check application logs: you should see "Firebase initialized for project '<projectId>'".
- Hit `/healthz` and a representative endpoint to confirm requests are served.
- If metrics are enabled, ensure `/metrics` is reachable.

## 4) Revoke the old key
1. In Google Cloud Console → Service Accounts → Keys, delete the previous (old) key.
2. Document the rotation: date/time, who performed, reason/change ticket.

## 5) Emergency rollback
- Keep the previous key available until verification completes.
- If needed, re-apply the previous secret (K8s) or revert the Docker secret file path, then rollout restart again.

## 6) Local development
- You can set `GOOGLE_APPLICATION_CREDENTIALS` to a directory containing your service account JSON, or to the JSON file path directly.
- Do NOT commit the JSON file. The repository `.gitignore` blocks `secrets/` and common key extensions.

## Notes
- The application reads `FIREBASE_PROJECT_ID` from a Kubernetes Secret (or env) and reads credentials from the mounted path at `GOOGLE_APPLICATION_CREDENTIALS`.
- Credentials may be provided either as a JSON string (env var) or as a file path; production should always use file-based secrets.
