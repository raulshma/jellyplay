plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.details"
    }

    sourceSets {
        // The MediaDetail-cluster screens (MediaDetailScreen + its body/
        // sections/sheets and the ManageSeries + navigation entryProvider)
        // share android + desktop verbatim: they carry the java.io/java.time/
        // java.text bodies and reach Room-backed data through commonMain
        // seams. SeerrDetail's files stay in commonMain (purified).
        // (The jvmShared middle source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // runCatchingRethrowingCancellation in resolveTargetItemIds (the
            // sanctioned wrapper — a bare runCatching there masked cancellation
            // of the canonicalEpisodeIds fetch).
            implementation(project(":shared:core:concurrency"))
            // Preference/state stores + projections (DetailStores bundle) and
            // the per-feature slices (Seerr, Downloads, Library, HomeDiscovery,
            // Experimental, PlayerEngine, AppRuntime).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // SeerrDetailUtils' purified date formatting: the java.time
            // "yyyy-MM-dd" parse moved onto kotlinx-datetime's LocalDate.parse.
            implementation(libs.kotlinx.datetime)
            // The navigation entry uses entry<Route> from the nav3 runtime/ui
            // artifacts only (syncplay/arrqueue precedent: the legacy
            // lifecycle-viewmodel-navigation3 edge is gone).
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
            // The shared FakeUserDataMutator double (test-scoped only — see
            // the fixtures module's house rules).
            implementation(project(":shared:core:test-fixtures"))
        }
        getByName("androidMain").dependencies {
            // The trailer-host actual delegates to InlineTrailerPlayer
            // (:shared:core:ui androidMain since the cutover). The
            // AudioPlaybackManager/ThemeMusicPlayer adapters
            // stay APP-side (AppKoinModule interop adapters; formerly the
            // HiltInteropModule singles) — a shared-module androidMain
            // actual would have to construct second instances. The share +
            // StatFs storage-probe actuals need no legacy types.
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.details.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
