# Incident Response Runbook

This runbook provides pragmatic steps to investigate and mitigate the most common production incidents for the SorenKai API.

Scope:
- Application (Ktor) issues
- MongoDB availability/performance
- Firebase authentication issues
- Rate limiting misconfiguration
- Deployment/rollout problems
- Elevated errors/latency (SLO breaches)

Prerequisites:
- Access to production Kubernetes cluster/namespace and observability stack (Grafana/Prometheus/logs)
- On-call laptop configured with kubectl context

## 0) Quick links
- Grafana dashboard: import `docs/observability/dashboards/api-overview.json` (or open your live dashboard)
- Prometheus alert rules: `deploy/monitoring/prometheus-rules.yaml`
- Kubernetes manifests: `deploy/k8s/*.yaml`
- Logs: use your central logging solution; app logs contain sanitized messages (no bearer tokens/emails)

## 1) High error rate (5xx) alert fires
1. Confirm in Grafana: panels "Error rate" and "Requests by status"; check timeframe around alert.
2. Inspect recent deployments:
   - `kubectl get deploy sorenkai-api -o yaml | grep image:`
   - `kubectl rollout history deployment/sorenkai-api`
3. Check pod status and logs:
   - `kubectl get pods -l app=sorenkai-api`
   - `kubectl logs deploy/sorenkai-api --tail=200`
4. Look for:
   - Unhandled exceptions (StatusPages will report INTERNAL)
   - Mongo connectivity errors or timeouts
   - Firebase token verification failures (may indicate config/secret issue)
5. Immediate mitigations:
   - Roll back to previous image if deploy-related: `kubectl rollout undo deployment/sorenkai-api`
   - If Mongo is degraded, see Section 3.
6. After mitigation, monitor error rate drops below thresholds; document root cause.

## 2) High latency (p95/p99) alert fires
1. Confirm Micrometer histograms on Grafana (Latency p95/p99 panel).
2. Check pod and node resource pressure (CPU/memory):
   - `kubectl top pods -l app=sorenkai-api`
   - `kubectl top nodes`
3. Check Mongo slow operations and CPU/IO; coordinate with DB owner.
4. Common causes & actions:
   - Sudden traffic spike: scale up replicas: `kubectl scale deploy/sorenkai-api --replicas=4` (verify HPA if used)
   - Oversized pageSize requests: server caps at 100; verify clients adhering and consider reducing cap if needed.
   - GC pressure: review JVM opts, container memory limits.

## 3) MongoDB unavailable/degraded
Symptoms: connection timeouts, 5xx from repository operations, error logs referencing Mongo driver.

Actions:
1. Verify DB health from app namespace:
   - `kubectl run -it mongo-ping --image=mongo:7.0 --rm -- bash -lc "mongosh \"$MONGODB_URI\" --eval 'db.adminCommand(\'ping\')'"`
2. If down:
   - Engage DB owner. If you own it, check StatefulSet/ReplicaSet health, PVC, disk space.
3. Read-only fallback? Not implemented; API requires Mongo for most endpoints. Communicate outage and pause deploys.
4. After recovery, validate indexes by restarting one API pod (repos ensure indexes on startup) or run a one-off job calling `initRepos()`.

## 4) Firebase auth failures
Symptoms: All authenticated routes 401; logs indicate Firebase initialization/verification errors.

Actions:
1. Check secrets mounted at `/var/run/secrets/google`; ensure `FIREBASE_PROJECT_ID` is correct.
2. Look for recent credential rotation; follow `docs/runbooks/firebase-credentials-rotation.md`.
3. If emergency, you can temporarily enable test auth bypass ONLY outside prod. In prod, do not enable bypass; fix credentials.

## 5) Rate limiting blocking legitimate traffic
Symptoms: Many 429/"RATE_LIMITED" errors; user reports inability to post.

Actions:
1. Verify rate limiter env vars (RATE_POST_LIMIT, RATE_*_WINDOW_SECONDS) in Deployment.
2. Check RTDB availability if configured via `FIREBASE_RTDB_URL`; fallback to in-memory occurs automatically but only per-instance.
3. Temporarily raise limits via ConfigMap/Deployment env, apply rollout, and monitor.

## 6) Bad rollout / partial outage during deploy
Mitigations already in place:
- RollingUpdate (maxUnavailable=0, maxSurge=1)
- Graceful shutdown with preStop drain and termination grace period

Actions:
1. If new pods fail readiness, use `kubectl describe pod` and `kubectl logs` to diagnose.
2. If necessary, rollback: `kubectl rollout undo deployment/sorenkai-api`.
3. Verify zero-downtime by tailing logs and probing `/healthz` during rollout.

## 7) No traffic alert
- Prometheus rule APINoTraffic may fire during off hours or if ingress misconfigured.
- Validate Service/Ingress/LoadBalancer routes; confirm DNS.

## 8) Communications and postmortem
- If user-visible impact occurs, post a status update via your chosen channel.
- After resolution, write a brief postmortem: timeline, impact, root cause, actions to prevent recurrence.

## 9) Contacts & ownership
- Primary: Solo admin (you). Keep this doc up-to-date with escalation contacts if team grows.

