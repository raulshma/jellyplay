pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "JellyPlay"
include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:ui")
include(":core:notification")
include(":core:testing")
include(":feature:auth")
include(":feature:home")
include(":feature:library")
include(":feature:search")
include(":feature:details")
include(":feature:player:core")
include(":feature:player:video")
include(":feature:player:audio")
include(":feature:player:live")
include(":feature:downloads")
include(":feature:settings")
include(":feature:subtitle-tester")
include(":feature:music")
include(":feature:livetv")
include(":feature:syncplay")
include(":feature:editor")
include(":feature:admin")
include(":feature:onboarding")
include(":feature:newsletter")
include(":feature:insights")
include(":feature:requests")
include(":feature:shortcuts")
include(":feature:arrqueue")
include(":feature:calendar")
include(":baselineprofile")
include(":baselineprofile-tv")

// KMP shell (docs/kmp-migration-plan.md): the parallel tree that legacy modules
// migrate into, phase by phase. Lives beside (not inside) the Android tree so
// the existing app keeps building untouched until cutover.
include(":shared:core:model")
include(":shared:core:designsystem")
include(":shared:core:datastore")
