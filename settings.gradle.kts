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
include(":feature:player:core")
include(":feature:player:video")
include(":feature:player:audio")
include(":feature:player:live")






include(":baselineprofile")
include(":baselineprofile-tv")

// KMP shell (docs/kmp-migration-plan.md): the parallel tree that legacy modules
// migrate into, phase by phase. Lives beside (not inside) the Android tree so
// the existing app keeps building untouched until cutover.
include(":shared:core:model")
include(":shared:core:designsystem")
include(":shared:core:datastore")
include(":shared:core:database")
include(":shared:core:network")
include(":shared:core:data")
include(":shared:core:ui")
include(":shared:core:player-contract")

// Feature conveyor (plan §Phase V3): one shared feature module per migration
// PR, same shape as the shared core stack above.
include(":shared:feature:search")
include(":shared:feature:library")
include(":shared:feature:music")
include(":shared:feature:livetv")
include(":shared:feature:downloads")
include(":shared:feature:syncplay")
include(":shared:feature:settings")
include(":shared:feature:admin")
include(":shared:feature:requests")

include(":shared:feature:newsletter")

include(":shared:feature:editor")

include(":shared:feature:calendar")


include(":shared:feature:shortcuts")

include(":shared:feature:insights")



include(":shared:feature:onboarding")

include(":shared:feature:arrqueue")




include(":shared:feature:subtitle-tester")
include(":shared:feature:details")


// Desktop shell (plan §Phase V1b)
include(":apps:desktop")

// Web shell (plan §Phase W): wasmJs/browser skeleton over the shared
// datastore DI stack; API transport (Ktor) and image engine (Coil) land
// with the W.1/W.4 slices.
include(":apps:web")
