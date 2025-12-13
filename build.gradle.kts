import dev.detekt.gradle.extensions.FailOnSeverity

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.ktor.plugin) apply false
    // Code style
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.ktlint) apply false
}

//val detektConfigFile = file("$rootDir/detekt.yml")
//if (!detektConfigFile.exists()) {
//    throw GradleException("Root detekt.yml not found at: $detektConfigFile")
//}


repositories {
    mavenCentral()
    google()
}

subprojects {
    group = "com.sorenkai"
    version = "2.0.0"

    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "dev.detekt")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint()
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            ktlint()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

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

//    extensions.configure<dev.detekt.gradle.Detekt> {
////        config = files(detektConfigFile)
//        buildUponDefaultConfig = false
//        autoCorrect = true
//        ignoreFailures = true
//        failOnSeverity = FailOnSeverity.Never
//    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask>().configureEach {
        enabled = false
    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask>().configureEach {
        enabled = false
    }
}

// Align Kotlin stdlib artifacts to the same version to avoid classpath conflicts during tests
//val kotlinVersion = libs.versions.kotlin.get()
//configurations.all {
//    resolutionStrategy.eachDependency {
//        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
//            useVersion(kotlinVersion)
//            because("Align Kotlin stdlib to $kotlinVersion for consistent runtime")
//        }
//    }
//}

// Globally exclude vulnerable commons-lang 2.x if any transitive tries to bring it
configurations.all {
    exclude(group = "commons-lang", module = "commons-lang")
}
