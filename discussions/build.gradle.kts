plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.discussions.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.google.pubsub)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
}
