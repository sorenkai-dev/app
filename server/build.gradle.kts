plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktor.plugin) // required for fatJar build + engine config
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    // ──────────────── Application Layer Dependencies ────────────────
    // Server depends on ALL business modules (they expose routes/use-cases)
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":auth"))
    implementation(project(":community"))
    implementation(project(":writings"))
    implementation(project(":admin"))
    implementation(project(":infrastructure"))

    // ──────────────── Ktor Server (HTTP engine + middleware) ────────────────
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.call.id)

    // ──────────────── Auth (needed ONLY for *wiring*, not logic) ─────────────
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    // DI
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // ──────────────── Observability / Metrics ────────────────
    implementation(libs.ktor.metrics.micrometer)
    implementation(libs.micrometer.prometheus)

    // ──────────────── Logging ────────────────
    implementation(libs.logback.classic)

    // ──────────────── Test Support ────────────────
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
