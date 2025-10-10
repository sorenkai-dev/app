import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktor.plugin)
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    application
}

group = "com.sorenkai"
version = "0.1.0"

kotlin {
    jvmToolchain(24)
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-opt-in=kotlin.time.ExperimentalTime"
            )
        )
    }
    sourceSets.all {
        languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
    }
}


application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
    google()
}

// Align Kotlin stdlib artifacts to the same version to avoid classpath conflicts during tests
val kotlinVersion = libs.versions.kotlin.get()
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
            useVersion(kotlinVersion)
            because("Align Kotlin stdlib to $kotlinVersion for consistent runtime")
        }
    }
}

detekt {
    // Detekt version
    toolVersion = "1.23.8"

    // Use your own YAML if you want to customize rules
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))

    // Generate default config (optional)
    buildUponDefaultConfig = true

    // Point Detekt’s internal Kotlin compiler to JDK 21
    // The property name here is *jvmTarget*, but you set it in build args, not directly
    basePath = projectDir.absolutePath
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    ignoreFailures = true
}
tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
    ignoreFailures = true
}

// Allow skipping Docker-dependent integration tests when NO_DOCKER=true (useful in CI or constrained envs)
tasks.test {
    useJUnitPlatform()
    val noDocker = System.getenv("NO_DOCKER")?.equals("true", ignoreCase = true) == true
    if (noDocker) {
        filter {
            excludeTestsMatching("com.sorenkai.api.integration.*PaginationIntegrationTest")
            excludeTestsMatching("com.sorenkai.api.integration.AdminRoutesIntegrationTest")
        }
    }
}

dependencies {
    // Ktor core
    implementation(libs.bundles.ktor.base)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.auth.jwt)

    // KotlinX, datetime, uuid
    implementation(libs.kotlinx.serialization.json)

    // JWT
    implementation(libs.auth0.java.jwt)

    // Google API Client for JWKS HTTPS fetch
    implementation(libs.google.api.client)

    // Mongo with BOM
    implementation(platform(libs.mongodb.bom))
    implementation(libs.mongodb.kotlin.coroutine)
    implementation(libs.mongodb.bson.kotlinx)

    // Firebase Admin
    implementation(libs.firebase.admin)

    // DI
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Observability
    implementation(libs.ktor.metrics.micrometer)
    implementation(libs.micrometer.prometheus)

    // Logging — exclude Janino (removes the CVEs)
    implementation(libs.logback.classic) {
        exclude(group = "org.codehaus.janino", module = "janino")
    }

    // Config YAML (optional but handy)
    implementation(libs.ktor.server.config.yaml)

    // Security: pin Apache Commons Lang3 to a safe version and override transitives
    implementation(libs.commons.lang3)
    implementation(libs.commons.compress)

    // Test
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Globally exclude vulnerable commons-lang 2.x if any transitive tries to bring it
configurations.all {
    exclude(group = "commons-lang", module = "commons-lang")
}
