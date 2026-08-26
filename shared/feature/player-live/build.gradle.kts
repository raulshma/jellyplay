import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.player.live"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target yet (same as every shared/feature module): the web
    // shell lands in plan §Phase W. This also keeps java.* legal in
    // commonMain (java.time.Instant/DateTimeFormatter in the player VM) —
    // same precedent as shared/core:data and :feature:syncplay.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // AppRuntimeStateStore/PlaybackStore/VideoPlayerAggregateStore.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
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
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
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
            // (screen-forward message collector) and
            // TranscodeReasonsFormatter (renderer seam actual) still live in
            // the legacy Android-only :core:ui shim until its own conveyor
            // move — same transition-period relationship as the livetv
            // conveyor's AndroidLiveTvMessenger, dies at Phase X.
            implementation(project(":core:ui"))
            // PlayerAudioLifecycle (audio-focus/becoming-noisy wrapper the
            // Media3LivePlayerAudio seam delegates to) still lives in the
            // legacy Android-only :core:data shim until its own conveyor
            // move.
            implementation(project(":core:data"))
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
// :feature:player:live so migrated files keep their
// `com.raulshma.jellyplay.feature.player.live` imports; generated accessors
// land in `...feature.player.live.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.player.live.generated.resources"
