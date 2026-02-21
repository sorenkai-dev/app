plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktor.plugin) // required for fatJar build + engine config
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("com.sorenkai.server.ApplicationKt")
}

detekt {
    config = files("$rootDir/detekt.yml")
}

dependencies {
    // ──────────────── Application Layer Dependencies ────────────────
    // Server depends on ALL business modules (they expose routes/use-cases)
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":auth"))
    // Exposed
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari.cp)
    implementation(libs.supabase.postgresql)
    implementation("org.postgresql:postgresql:42.7.5")

    implementation(platform(libs.mongodb.bom))
    implementation(libs.mongodb.kotlin.coroutine)
    implementation(project(":community"))
    implementation(project(":writings"))
    implementation(project(":infrastructure"))
    implementation(project(":reports"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

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

    // ──────────────── Redis (Jedis TCP client) ────────────────
    implementation("redis.clients:jedis:5.2.0")

    // ──────────────── Test Support ────────────────
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
