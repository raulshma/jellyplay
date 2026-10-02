plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.player.live"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // EngineEventCoordinator (the shared engine-event policy core the
            // live VM consumes) + PlayerChromePolicies (the TV controls-timeout
            // fold and the DVR-window refresh tick). Explicit edge — also
            // reachable transitively through core:data's api(player-contract).
            implementation(project(":shared:core:player-contract"))
            // AppRuntimeStateStore/PlaybackStore/VideoPlayerAggregateStore.
            implementation(project(":shared:core:datastore"))
            // Cancellation-safe suspend wrappers — the live player VM's
            // record/refresh paths must not mask cancellation.
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:ui"))
            // RecordActions — livetv's ONE record/cancel choreography the
            // in-player record quartet delegates to (the VM used to hand-copy
            // it). Feature-to-feature edge like shell → livetv and
            // subtitle-tester → player-video; livetv only depends on the core
            // modules, so the graph stays acyclic.
            implementation(project(":shared:feature:livetv"))
            // (The legacy build's lifecycle-viewmodel-navigation3 and
            // hilt-navigation-compose edges were dropped: no file imports
            // them — navigation entries use entry<Route> from the nav3
            // runtime/ui artifacts only, and the screen's ViewModel is
            // Koin-owned via koinViewModel.)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // The media surface is Android-only by design: the screen (media3
        // PlayerView/AndroidView), ExoLiveEngine + its OkHttp data source,
        // the factory/audio/renderer seam actuals and the D-pad seek bar all
        // ride androidx.media3, which has no JVM artifact. The jvm target
        // compiles the shared ViewModel/logic and hosts its tests; the
        // section stays latent on desktop (livetv keeps
        // Route.LiveTvChannelPlayer guarded there — same documented-latent
        // state as the player-adjacent features).
        getByName("androidMain").dependencies {
            // findActivity (PlayerView window wiring), LocalUserMessageBus
            // (screen-forward message collector), TranscodeReasonsFormatter
            // (renderer seam actual) and PlayerAudioLifecycle (audio-focus/
            // becoming-noisy wrapper the Media3LivePlayerAudio seam delegates
            // to) live in :shared:core:{ui,data} androidMain since the
            // cutover dissolved the legacy core modules.
            // ExoLiveEngine + ExoLiveEngineFactory (streaming OkHttpClient).
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.datasource)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.okhttp)
            // PlayerWindowOps / PlayerOrientationLock / rememberPlayerWindowOps
            // — the host-window seam (system bars, keep-screen-on, orientation
            // lock) LivePlayerScreen's window effects cite instead of their
            // former byte-identical WindowCompat/FLAG_ inline code. The edge
            // lives in androidMain because the live screen is androidMain-only
            // (the jvm target never sees the player). Feature-to-feature edge
            // like livetv above; player-video only depends on the core modules
            // + player-contract, so the graph stays acyclic.
            implementation(project(":shared:feature:player-video"))
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.player.live.generated.resources`, same as the legacy value) is
// a path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
