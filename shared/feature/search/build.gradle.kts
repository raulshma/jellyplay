plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.search"
    }

    sourceSets {
        // The jvmShared slice (now empty of Kotlin — the QuickDownloadActions
        // wrapper/fragment actuals were deleted when core:data took over the
        // seam), kept so android + desktop share any future JVM-only Kotlin
        // like the newsletter/requests jvmShared source sets. (The jvmShared
        // middle source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // SearchFiltersStore (persisted search filter blob).
            implementation(project(":shared:core:datastore"))
            // runCatchingRethrowingCancellation for the best-effort filter
            // persist/clear writes (bare runCatching would swallow the
            // caller's cancellation).
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:ui"))
            implementation(libs.jb.compose.saveable)
            implementation(libs.paging.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // androidMain needs no explicit deps: the voice-search actual's
        // androidx.activity.compose rides navigation3-ui's transitive edge,
        // same as shared/core/ui's BackHandler seam.
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.search.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
