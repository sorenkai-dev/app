plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    // Domain core (IDs, shared domain models, error types, base interfaces)
    implementation(project(":core"))

    // Common utilities (optional — but safe to include for future helpers)
    implementation(project(":common"))
    implementation(project(":auth"))

    // Serialization for community DTOs / requests / responses
    implementation(libs.kotlinx.serialization.json)

    // Unit Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
