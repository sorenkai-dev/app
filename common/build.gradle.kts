plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(24)
}

dependencies {

    // Serialization utilities used project-wide
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback.classic)

    // BSON types (ObjectId) used by util.newId()
    implementation(platform(libs.mongodb.bom))
    implementation(libs.mongodb.bson.kotlinx)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}
