plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(24)
}

dependencies {

    implementation(project(":core"))
    implementation(project(":common"))
    // ──────────────── Core Language / Serialization ────────────────
    implementation(libs.kotlinx.serialization.json)

    // ──────────────── Unit Testing ────────────────
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
