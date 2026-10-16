rootProject.name = "app"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}

include(":analytics")
include(":api")
include(":auth")
include(":audit")
include(":common")
include(":discussions")

include(":enrichment")
include(":writings")
