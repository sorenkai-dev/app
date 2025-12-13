plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    // Domain services and repositories live here
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Models from community module
    implementation(project(":common"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
