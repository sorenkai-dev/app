rootProject.name = "app"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}

include(":auth")
include(":core")
include(":common")
include(":infrastructure")
include(":community")
include(":writings")
include(":server")
include(":reports")
