# ---- Build stage -------------------------------------------------------------
FROM eclipse-temurin:24-jdk AS build

WORKDIR /workspace

# Gradle wrapper & root config (cache-friendly)
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .
COPY gradle/libs.versions.toml gradle/libs.versions.toml

RUN chmod +x ./gradlew

# Copy module build files early (Renovate-safe)
COPY auth/build.gradle.kts auth/
COPY common/build.gradle.kts common/
COPY community/build.gradle.kts community/
COPY core/build.gradle.kts core/
COPY infrastructure/build.gradle.kts infrastructure/
COPY reports/build.gradle.kts reports/
COPY server/build.gradle.kts server/
COPY writings/build.gradle.kts writings/

# Warm dependency cache
RUN ./gradlew --no-daemon -x test help || true

# Copy full source tree
COPY auth auth
COPY common common
COPY community community
COPY core core
COPY infrastructure infrastructure
COPY reports reports
COPY server server
COPY writings writings

# Build only the server fat jar
RUN ./gradlew --no-daemon :server:buildFatJar

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:24-jre

ENV LANG=C.UTF-8

# Install curl (for healthcheck only)
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN useradd -ms /bin/bash appuser
WORKDIR /app

# Copy server fat JAR
COPY --from=build /workspace/server/build/libs/*-all.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV PORT=8080 \
    JAVA_OPTS="" \
    ENABLE_JVM_METRICS=true

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/serverhealth" > /dev/null || exit 1

ENTRYPOINT ["sh", "-lc", "exec java -Djansi.passthrough=true $JAVA_OPTS -jar /app/app.jar -port=${PORT}"]
