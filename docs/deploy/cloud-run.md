# Deploying to Google Cloud Run (Recommended, Low Cost)

This guide walks you through deploying the SorenKai backend to Google Cloud Run with minimal cost and ops overhead. It uses:
- Cloud Run (fully managed, scales to zero)
- Artifact Registry (container registry)
- Secret Manager (for Firebase Service Account JSON)
- MongoDB Atlas (managed Mongo; free/shared tier is fine to start)

Prerequisites:
- A GCP project (PROJECT_ID) with billing enabled
- gcloud CLI installed and authenticated: `gcloud auth login` and `gcloud config set project <PROJECT_ID>`
- A MongoDB Atlas cluster/DB with a user and the connection string (MONGODB_URI)
- Your Firebase Admin Service Account JSON file

## 1) Enable required APIs

```bash
PROJECT_ID=<your-project-id>
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com
```

## 2) Create Artifact Registry repository

Choose a region. us-central1 is a solid default for cost/latency.

```bash
REGION=us-central1
REPO=sorenkai

# Create a Docker repository in Artifact Registry
gcloud artifacts repositories create $REPO \
  --repository-format=docker \
  --location=$REGION \
  --description="SorenKai backend images"

# Configure Docker to authenticate to Artifact Registry
gcloud auth configure-docker ${REGION}-docker.pkg.dev
```

## 3) Build and push the image (v1.0.0)

You can build locally or via CI. Local example:

```bash
IMAGE=${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/backend:1.0.0

# From repo root
docker build -t $IMAGE .
docker push $IMAGE
```

(If you prefer CI later, set up a GitHub Actions job with Workload Identity Federation to push to Artifact Registry.)

## 4) Store the Firebase Service Account in Secret Manager

```bash
# Create a secret (name it clearly)
FIREBASE_SA_SECRET=firebase-service-account

# Upload the JSON (replace path)
gcloud secrets create $FIREBASE_SA_SECRET --replication-policy=automatic

gcloud secrets versions add $FIREBASE_SA_SECRET \
  --data-file=/path/to/your/firebase-service-account.json

# Grant Cloud Run runtime access to read this secret later
# (We’ll attach the Cloud Run service account shortly.)
```

## 5) Prepare environment variables

Decide values for staging and production. Examples:

- Common
  - PORT=8080 (Cloud Run sets PORT; we pass it through)
  - FIREBASE_PROJECT_ID=<your-firebase-project-id>
  - FIREBASE_RTDB_URL=<optional RTDB URL if using distributed rate limit>
  - MAX_UPLOAD_MB=10
  - RATE_POST_WINDOW_SECONDS=60, RATE_POST_LIMIT=1 (adjust), similar for comments/reports

- Staging
  - ENV=staging
  - ALLOWED_ORIGINS="https://staging.sorenkai.com,http://localhost:5173"
  - SWAGGER_ENABLED=true  # allow docs in staging

- Production
  - ENV=prod
  - ALLOWED_ORIGINS="https://app.sorenkai.com"  # your frontend prod domain
  - ENABLE_HSTS=true  # extra safeguard (also auto-enabled in prod)

- Secrets
  - GOOGLE_APPLICATION_CREDENTIALS will point to the mounted secret path (file) at runtime
  - MONGODB_URI from Atlas (store as a Secret for prod; staging can be env var or secret)

## 6) Create the staging Cloud Run service

```bash
SERVICE_STG=sorenkai-api-staging
REGION=us-central1
IMAGE=${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/backend:1.0.0
FIREBASE_SA_SECRET=firebase-service-account

# Create service (allow unauthenticated if public API; remove --allow-unauthenticated if private)
gcloud run deploy $SERVICE_STG \
  --image=$IMAGE \
  --region=$REGION \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --min-instances=0 \
  --max-instances=4 \
  --set-env-vars=ENV=staging,PORT=8080,MAX_UPLOAD_MB=10,SWAGGER_ENABLED=true \
  --set-env-vars=ALLOWED_ORIGINS="https://staging.sorenkai.com,http://localhost:5173" \
  --set-env-vars=FIREBASE_PROJECT_ID=<your-firebase-project-id> \
  --set-env-vars=MONGODB_URI="<your-staging-mongodb-uri>" \
  --set-env-vars=FIREBASE_RTDB_URL="" \
  --update-secrets=GOOGLE_APPLICATION_CREDENTIALS=${FIREBASE_SA_SECRET}:latest:/var/run/secrets/google/firebase-service-account.json
```

Notes:
- The `--update-secrets` flag mounts the secret to a file path inside the container and sets the env var to that file path, matching our app’s expectation.
- You can add `--cpu-throttling` defaults (Cloud Run handles this) and tune min/max instances later.

Grant the service runtime access to the secret if prompted:
```bash
SA_EMAIL=$(gcloud run services describe $SERVICE_STG --region=$REGION --format='value(template.spec.serviceAccountName)')
# If empty, set a service account explicitly:
if [ -z "$SA_EMAIL" ]; then
  SA_NAME=sorenkai-api-staging
  gcloud iam service-accounts create $SA_NAME --display-name="SorenKai API Staging"
  SA_EMAIL=${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com
  gcloud run services update $SERVICE_STG --region=$REGION --service-account=$SA_EMAIL
fi

# Allow the service account to access the secret
gcloud secrets add-iam-policy-binding $FIREBASE_SA_SECRET \
  --member=serviceAccount:${SA_EMAIL} \
  --role=roles/secretmanager.secretAccessor
```

## 7) Smoke test staging

```bash
STAGING_URL=$(gcloud run services describe $SERVICE_STG --region=$REGION --format='value(status.url)')

# Health
curl -fsS "$STAGING_URL/serverhealth" | jq '.'

# Stories list
curl -fsS "$STAGING_URL/v1/stories" | jq '.'

# Optional: enable test auth bypass for quick local tests (non-prod only)
# Cloud Run env can include TEST_AUTH_ENABLED=true and TEST_AUTH_UID=demo for temporary testing.
```

## 8) (Optional) Map a custom staging domain

```bash
# Verify domain ownership in Search Console or Cloud Domains
# Then map domain to the service (this will provision managed SSL):
DOMAIN=api-staging.sorenkai.com
gcloud run domain-mappings create --service $SERVICE_STG --region $REGION --domain $DOMAIN
```

## 9) Create the production Cloud Run service

Repeat with prod settings:

```bash
SERVICE_PROD=sorenkai-api

gcloud run deploy $SERVICE_PROD \
  --image=$IMAGE \
  --region=$REGION \
  --platform=managed \
  --allow-unauthenticated \
  --port=8080 \
  --min-instances=0 \
  --max-instances=6 \
  --set-env-vars=ENV=prod,PORT=8080,MAX_UPLOAD_MB=10,ENABLE_HSTS=true \
  --set-env-vars=ALLOWED_ORIGINS="https://app.sorenkai.com" \
  --set-env-vars=FIREBASE_PROJECT_ID=<your-firebase-project-id> \
  --set-env-vars=MONGODB_URI="<your-prod-mongodb-uri>" \
  --set-env-vars=FIREBASE_RTDB_URL="" \
  --update-secrets=GOOGLE_APPLICATION_CREDENTIALS=${FIREBASE_SA_SECRET}:latest:/var/run/secrets/google/firebase-service-account.json

# Grant secret access (similar to staging)
SA_EMAIL=$(gcloud run services describe $SERVICE_PROD --region=$REGION --format='value(template.spec.serviceAccountName)')
if [ -z "$SA_EMAIL" ]; then
  SA_NAME=sorenkai-api
  gcloud iam service-accounts create $SA_NAME --display-name="SorenKai API Prod"
  SA_EMAIL=${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com
  gcloud run services update $SERVICE_PROD --region=$REGION --service-account=$SA_EMAIL
fi

gcloud secrets add-iam-policy-binding $FIREBASE_SA_SECRET \
  --member=serviceAccount:${SA_EMAIL} \
  --role=roles/secretmanager.secretAccessor
```

(Optional) Map the production domain (e.g., api.sorenkai.com) similarly to staging.

## 10) Rollouts and updates

When you cut a new version (e.g., 1.0.1):

```bash
IMAGE=${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}/backend:1.0.1

docker build -t $IMAGE .
docker push $IMAGE

gcloud run deploy $SERVICE_STG --image=$IMAGE --region=$REGION
# Smoke test, then promote to prod
gcloud run deploy $SERVICE_PROD --image=$IMAGE --region=$REGION
```

## 11) Notes and tips

- Cost: Keep min-instances=0 for both staging and prod to leverage scale-to-zero when idle.
- CORS: Update ALLOWED_ORIGINS per environment (frontend origins only). You can include multiple origins separated by commas.
- Docs: SWAGGER_ENABLED=true should only be on in non-prod environments.
- HSTS: Enabled automatically in prod; can force with ENABLE_HSTS=true.
- Metrics: /metrics is available; integrate with a Prometheus-compatible scraper if you have one.
- Security: Never hardcode secrets. Use Secret Manager and IAM. The app supports a secret file for Firebase via GOOGLE_APPLICATION_CREDENTIALS.
- Future improvement: Replace service account key JSON by using Application Default Credentials with a service account attached to Cloud Run (code update needed to use ADC path). For now, the secret JSON approach is compatible with the current code.



## Staging smoke test via GitHub Actions
To verify a staged Cloud Run deployment is reachable, use the CI workflow’s optional smoke test.

Ad-hoc run (no secret needed):
- In GitHub → Actions → CI → Run workflow
- Enter your staging base URL in the `smoke_url` input (e.g., https://your-staging-domain)
- Run the workflow; the "Smoke Test" job will curl `${smoke_url}/serverhealth`

Automated on every push:
- Set repository secret `SMOKE_URL` to your staging base URL
- The smoke test will execute after the build/test job on each CI run

Endpoint:
- `/serverhealth` is unauthenticated and returns a small JSON body when healthy
