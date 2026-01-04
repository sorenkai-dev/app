plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    // Core domain types (UserId, Writings domain, ValidationError, etc.)
    implementation(project(":core"))

    // Common utilities (future home of error wrappers, Result types, helpers)
    implementation(project(":common"))

    // Serialization (often required in infrastructure mappers / DTOs)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.mongodb.bom))
    implementation(libs.mongodb.bson.kotlinx)
    implementation(libs.mongodb.kotlin.coroutine)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)

    // Testcontainers for Mongo integration tests
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.mongodb)
}
