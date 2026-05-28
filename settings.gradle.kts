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
include(":feature:auth")
include(":feature:home")
include(":feature:library")
include(":feature:search")
include(":feature:details")
include(":feature:player:video")
include(":feature:player:audio")
include(":feature:downloads")
include(":feature:settings")
include(":feature:music")
include(":feature:livetv")
include(":feature:syncplay")
include(":feature:editor")
include(":feature:admin")
