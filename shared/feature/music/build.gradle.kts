plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.music"
    }

    sourceSets {
        // java.util.UUID (mood/smart playlist ids) ported to the stdlib
        // kotlin.uuid.Uuid (same v4 string shape —'s outbox-id precedent).
        //
        // The JVM-side bindings (MusicQueuePlayer -> JvmMusicQueuePlayer over
        // the AudioQueueFacade single) — the player-audio jvmShared
        // platform-module pattern. The former MusicTrackDownloads binding
        // moved to core:data (TrackDownloadStatusWindow / ActiveDownloadCount)
        // with the download-actions seam consolidation. (The jvmShared middle
        // source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // HomeDiscoveryStore (music home discovery prefs).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // GenreDetailViewModel ctor arg (nav-entry SavedStateHandle, KMP
            // since lifecycle 2.9 — synthesized from CreationExtras by Koin's
            // Android parameters holder, same extras the Hilt factory used).
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.paging.compose)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // androidMain needs no deps: unlike the library conveyor item (whose
        // user-messenger actual lives in the module), music's MusicMessageBus
        // Android actual is app-provided — it bridges to the Koin-owned
        // UserMessageBus (:shared:core:ui androidMain since the
        // cutover).
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.music.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
