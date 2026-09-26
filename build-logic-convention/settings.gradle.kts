// Included build providing the class-based convention plugins for the shared/
// KMP library modules (jellyplay.kmp.library.*). Included from the root
// settings via pluginManagement { includeBuild(...) }.
dependencyResolutionManagement {
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
    // Same catalog as the main build, so the convention plugins put EXACTLY
    // the AGP/Kotlin/Compose plugin versions the root build pins on their
    // own classpath — the versions here are authoritative for every module
    // that applies jellyplay.kmp.library.*.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic-convention"
