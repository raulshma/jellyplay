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
        // KGP tool-distribution governance (wave 13C — the settings-level
        // decision the wave-12D lanes deferred, see the wasmJsNodeTest notes
        // in shared/core/{model,ui}/build.gradle.kts): Kotlin's
        // kotlinWasmNodeJsSetup / kotlinWasmYarnSetup tasks DOWNLOAD their
        // tool archives through an ivy repository they register on the
        // PROJECT at task-graph time (KGP 2.3.21
        // targets/js/AbstractSetupTask.withUrlRepo), which
        // FAIL_ON_PROJECT_REPOS rejects outright ("'Distributions at
        // https://nodejs.org/dist' was added by unknown code" — declaring a
        // lookalike here does NOT help; the detector fires on the add
        // itself). The working split, using KGP's documented escape hatch
        // (EnvSpec.downloadBaseUrl: "If the property has no value,
        // repository is not added, so this can be used to add your own
        // repository"): the ROOT build script nulls the wasm node/yarn
        // downloadBaseUrl properties so KGP adds nothing, and THESE
        // settings-owned repos serve the exact coordinates KGP resolves —
        //   org.nodejs:node:<ver>:<platform>-<arch>@zip from
        //   https://nodejs.org/dist "v[revision]/[artifact](-v[revision]-[classifier]).[ext]"
        //   com.yarnpkg:yarn:<ver>@tar.gz from
        //   https://github.com/yarnpkg/yarn/releases/download
        //     "v[revision]/[artifact](-v[revision]).[ext]"
        // (both artifact-metadata-only ivy repos; patternLayout and
        // metadataSources mirror KGP's own registration exactly, with a
        // deliberately broader includeGroup filter where KGP uses
        // includeModule). Group-scoped content filters keep the
        // repositories from serving anything but tool distributions. Net
        // effect: FAIL_ON_PROJECT_REPOS keeps failing real ungoverned repos,
        // while webpack and the wasmJsNodeTest lanes run with no
        // PREFER_PROJECT flip anywhere.
        ivy("https://nodejs.org/dist") {
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeGroup("org.nodejs") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources { artifact() }
            content { includeGroup("com.yarnpkg") }
        }
    }
}

rootProject.name = "JellyPlay"
include(":app")
include(":core:data")
include(":core:ui")
include(":core:notification")
include(":core:testing")






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

include(":shared:feature:home")




include(":shared:feature:subtitle-tester")
include(":shared:feature:player-live")
include(":shared:feature:player-video")
include(":shared:feature:details")

include(":shared:feature:auth")
include(":shared:feature:player-audio")


// Desktop shell (plan §Phase V1b)
include(":apps:desktop")

// Web shell (plan §Phase W): wasmJs/browser skeleton over the shared
// datastore DI stack; API transport (Ktor) and image engine (Coil) land
// with the W.1/W.4 slices.
include(":apps:web")
