plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    // Core domain contracts (IDs, shared domain errors, base entities)
    implementation(project(":core"))

    // Common utilities (optional for DTO helpers, result types, etc.)
    implementation(project(":common"))
    implementation(project(":auth"))

    // Serialization for writing metadata, DTO definitions, etc.
    implementation(libs.kotlinx.serialization.json)

    // Unit testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
