import org.gradle.api.plugins.ExtensionAware

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
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 and
            // hilt-navigation-compose edges were dropped: no file imports
            // them — navigation entries use entry<Route> from the nav3
            // runtime/ui artifacts only, and the screen's ViewModel is
            // Koin-owned via koinViewModel.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screen.
            implementation(libs.lifecycle.runtime.compose)
            // Koin owns the player ViewModel (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
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
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// feature:player:live so migrated files keep their
// `com.raulshma.jellyplay.feature.player.live` imports; generated accessors
// land in `...feature.player.live.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.player.live.generated.resources"
