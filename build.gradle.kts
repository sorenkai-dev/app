import dev.detekt.gradle.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.ktor.plugin) apply false
    // Code style
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}



repositories {
    mavenCentral()
    google()
}

subprojects {
    group = "com.sorenkai"
    version = "2.0.0"

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")


    // Ktlint plugin defaults
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(false)
        verbose.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(true)
        filter {
            exclude("**/build/**")
        }
    }

    plugins.withId("dev.detekt") {
        extensions.configure<DetektExtension> {
            // Keep using the root config
            config.setFrom("${rootDir}/detekt.yml")

            // Only analyze main Kotlin sources for each module
            source.setFrom(files("src/main/kotlin"))
        }
    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask>().configureEach {
        enabled = false
    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask>().configureEach {
        enabled = false
    }
}


// Globally exclude vulnerable commons-lang 2.x if any transitive tries to bring it
configurations.all {
    exclude(group = "commons-lang", module = "commons-lang")
}
