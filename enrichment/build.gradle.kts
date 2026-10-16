plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.exposed.core)
    implementation(libs.exposed.datetime)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.hikari.cp)
    implementation(libs.postgresql.jdbc)
    implementation(libs.kotlinx.serialization.json)
}
