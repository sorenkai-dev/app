# SorenKai Backend (Ktor)

Backend service for [sorenkai.com](https://www.sorenkai.com) powering community features like story submissions, comments, and reports.
Built with [Ktor](https://ktor.io/), [MongoDB](https://www.mongodb.com/), and [Firebase](https://firebase.google.com/) (Authentication + Realtime Database). Firebase is required.

Useful Ktor links:
- Ktor docs: https://ktor.io/docs/home.html
- Ktor GitHub: https://github.com/ktorio/ktor

## Features

This project uses Ktor 2.x with a small, composable module setup:
- Routing, content negotiation, and JSON via kotlinx.serialization
- Request/response logging and default headers
- Firebase Authentication (JWT via Firebase ID tokens). See Security.kt
- Health and metrics endpoints (see Monitoring.kt)
- OpenAPI spec available in `src/main/resources/openapi/documentation.yaml` (serve via Swagger/OpenAPI if enabled)
- Rate limiting/post interval tracking via Firebase Realtime Database

## Project structure

```
src/main/kotlin/
├─ Application.kt        # Application entrypoint
├─ Routing.kt            # Routes wiring
├─ Serialization.kt      # JSON configuration
├─ Security.kt           # Auth/JWT configuration (Firebase Authentication)
├─ Monitoring.kt         # Health/metrics exposure
├─ HTTP.kt               # HTTP config/helpers
├─ Databases.kt          # Database wiring (MongoDB, etc.)
├─ Frameworks.kt         # DI/other frameworks wiring
├─ HelloService.kt       # Example service
└─ CarsSchema.kt         # Example schema/model

src/main/resources/
├─ application.yaml      # Runtime configuration
├─ logback.xml           # Logging config
└─ openapi/documentation.yaml  # OpenAPI spec
```

## Quick start

Prerequisites:
- JDK 17+
- MongoDB accessible (if you enable DB features)
- Firebase service account (required for Authentication and Realtime Database)

Run locally (Windows PowerShell):
- .\gradlew.bat run

Build and test:
- .\gradlew.bat test
- .\gradlew.bat build

If the server starts successfully, you'll see logs like:
```
INFO  Application - Application started
INFO  Application - Responding at http://0.0.0.0:8080
```

## Configuration

This project uses `application.yaml`. Environment variables can override config at runtime (typical in Ktor and containerized deployments).

Common variables:
- PORT: server port (default 8080)
- MONGODB_URI: MongoDB connection string
- FIREBASE_PROJECT_ID: Firebase project id (required)
- GOOGLE_APPLICATION_CREDENTIALS: path or inline JSON to service account (required)
- ALLOWED_ORIGINS: comma-separated CORS origins
- FIREBASE_DATABASE_URL: Firebase Realtime Database URL used for rate limiting/tracking

Example application.yaml snippet:
```
ktor:
  deployment:
    port: 8080
  application:
    modules:
      - ApplicationKt.module
```

## Authentication

Firebase Authentication is mandatory. Requests that require auth must include:
- Authorization: Bearer <Firebase ID Token>

Custom claims can enforce ToS/Privacy acceptance and roles (admin/moderator). See Security.kt for details.

## Rate limiting

Posting intervals and basic rate limiting are enforced via Firebase Realtime Database. Ensure the service account has database access and FIREBASE_DATABASE_URL is configured.

## Monitoring

- Health: GET /healthz
- Metrics: GET /metrics (Prometheus format)

## API docs

- OpenAPI spec: src/main/resources/openapi/documentation.yaml
- Swagger UI: If the Swagger/OpenAPI plugins are enabled, the UI will be served at the configured route (e.g., /swagger or /docs).

## License

Proprietary. 

All rights reserved © The Kindred LLC, 2025.

## Contact

- Website: [www.sorenkai.com](https://www.sorenkai.com)
- Legal: [legal@sorenkai.com](mailto:legal@sorenkai.com?subject=Legal)
- Reports: [report@sorenkai.com](mailto:report@sorenkai.com?subject=Report)
