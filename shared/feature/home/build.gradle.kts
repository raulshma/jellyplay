plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.home"
    }

    sourceSets {
        // The JVM-side bindings (the seam adapters over the core:data
        // jvmShared singles, plus the SyncStatusStateHolderFactory single
        // whose deps are jvmShared) — the player-audio jvmShared
        // platform-module pattern. (The jvmShared middle source set comes
        // from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Cancellation-safe suspend wrapper for the offline gate's
            // cached-layout read.
            implementation(project(":shared:core:concurrency"))
            //  ripple: ArrRepository's calendar window now takes
            // kotlinx.datetime.LocalDate — HomeRefresher converts java.time at the boundary.
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only.)
            // collectAsStateWithLifecycle + LocalLifecycleOwner (hero rotation
            // RESUMED gate) in the screens.
            // coil3.Size in HomeHero/HomeMediaRows image requests — coil3 is
            // not api-exported by shared/core:ui (insights precedent).
            implementation(libs.coil.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
            // The shared FakeUserDataMutator double, replacing the private
            // twin this suite used to carry (test-scoped only — see the
            // fixtures module's house rules).
            implementation(project(":shared:core:test-fixtures"))
        }
        getByName("androidMain").dependencies {
            // The process-lifecycle actual (refresher start/stop on app
            // foreground/background) registers against
            // ProcessLifecycleOwner — Android-only API, same androidMain dep
            // as shared/core:data's AndroidOfflineModeManager.
            implementation(libs.lifecycle.process)
            // The report-fully-drawn actual reads LocalActivity (TTFD metric).
            implementation(libs.androidx.activity.compose)
        }
    }
}

// The compose-resources `packageOfResClass` (`...feature.home.generated.resources`,
// same as the legacy value) is a path-derived default from the convention
// plugin now — see KmpLibraryComposePlugin.
