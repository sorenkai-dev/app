# ---- Build stage -------------------------------------------------------------
FROM eclipse-temurin:24-jdk AS build

# Optionally accept a Mongo URI at build-time (useful for baking defaults in dev images)
ARG MONGODB_URI="mongodb://localhost:27017/sorenkai?retryWrites=false"
ENV MONGODB_URI=${MONGODB_URI}

WORKDIR /workspace

# Copy only files that affect dependency resolution first (better caching)
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle.properties .
COPY gradle/libs.versions.toml gradle/libs.versions.toml

# Ensure wrapper is executable
RUN chmod +x ./gradlew

# Pre-fetch dependencies (cache-friendly warmup)
RUN ./gradlew --no-daemon --stacktrace -x test help || true

# Now copy sources
COPY src src

# Build a fat jar using the Ktor/Shadow task configured by the Ktor plugin
RUN ./gradlew --no-daemon --stacktrace buildFatJar

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:24-jre

# Accept the same build arg in the runtime stage and expose it as ENV (overridable at run-time)
ARG MONGODB_URI="mongodb://localhost:27017/sorenkai?retryWrites=false"
ENV MONGODB_URI=${MONGODB_URI}

ENV LANG=C.UTF-8

# Install curl for healthcheck (do this as root before switching user)
RUN apt-get update \
 && apt-get upgrade -y \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* # Clean up the cache to keep the image size small

# Create a non-root user for security
RUN useradd -ms /bin/bash appuser

WORKDIR /app

# Copy fat JAR produced by the build stage
COPY --from=build /workspace/build/libs/*-all.jar /app/app.jar

# Drop privileges
USER appuser

# Expose default Ktor port
EXPOSE 8080

# Environment defaults (can be overridden at runtime)
ENV PORT=8080 \
    JAVA_OPTS="" \
    ENABLE_JVM_METRICS=true

# Healthcheck: use curl (wget isn't in the base image by default)
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/serverhealth" > /dev/null || exit 1

# Run
ENTRYPOINT [ "sh", "-lc", "exec java -Djansi.passthrough=true $JAVA_OPTS -jar /app/app.jar -port=${PORT}" ]
