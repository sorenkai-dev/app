plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    // Core domain contracts (IDs, shared domain errors, base entities)
    implementation(project(":common"))

    implementation(platform(libs.mongodb.bom))
    implementation(libs.mongodb.kotlin.coroutine)
    implementation(libs.mongodb.bson.kotlinx)

    implementation(libs.apache.poi.oomxl)

    // Serialization for writing metadata, DTO definitions, etc.
    implementation(libs.kotlinx.serialization.json)

    // Unit testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.kotlinx.coroutines.test)
}
