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
    // Class-based convention plugins for the shared/ KMP library modules
    // (jellyplay.kmp.library.*). The script-plugin form (precompiled script
    // plugin in an included build) was tried and rejected: type-safe accessors
    // do not generate for source-set manipulation from a precompiled script
    // plugin's transitive classpath ("KotlinSourceSet with name 'jvmMain' not
    // found" through every withPlugin-guard variant). The class-based shape —
    // plugin types used by classpath (KotlinSourceSetContainer API), not
    // accessors — is the one that works.
    includeBuild("build-logic-convention")
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
        // KCEF (the desktop EPUB host) resolves jogamp gluegen/jogl through
        // JogAmp's own repository — not mirrored on Maven Central.
        maven { url = uri("https://jogamp.org/deployment/maven") }
    }
}

rootProject.name = "JellyPlay"
include(":app")






include(":baselineprofile")

// KMP shell (docs/kmp-migration-plan.md): the parallel tree that legacy modules
// migrate into, phase by phase. Lives beside (not inside) the Android tree so
// the existing app keeps building untouched until cutover.
include(":shared:core:concurrency")
include(":shared:core:model")
include(":shared:core:designsystem")
include(":shared:core:datastore")
include(":shared:core:database")
include(":shared:core:network")
include(":shared:core:data")
include(":shared:core:ui")
include(":shared:core:player-contract")

// Test doubles shared across feature jvmTest lanes. AGP 9 has no KMP
// testFixtures support, so this is a plain library consumed test-scoped
// only — never a main source set dependency (see its build.gradle.kts).
include(":shared:core:test-fixtures")

// Feature conveyor: one shared feature module per migration
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

include(":shared:feature:home")




include(":shared:feature:subtitle-tester")
include(":shared:feature:player-live")
include(":shared:feature:player-video")
include(":shared:feature:details")

// Shell-graph aggregator (CONTEXT.md "Shared appSections nav graph"): the one
// module that depends on every feature whose *Section builder the two shells
// used to restate — appSections + ShellHostHooks + the derived registration
// ledger the desktop dead-end guard reads.
include(":shared:feature:shell")

include(":shared:feature:auth")
include(":shared:feature:player-audio")
include(":shared:feature:player-book")


// Desktop shell
include(":apps:desktop")
