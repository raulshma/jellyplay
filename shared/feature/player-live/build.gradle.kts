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
        // Compose-resources packaging (device-pass finding): with the
        // AGP-9 KMP library plugin, android resources are OFF by default, so
        // copyAndroidMainComposeResourcesToAndroidAssets never runs and the
        // app APK ships this module's Res accessors with NO backing .cvr
        // assets — runtime MissingResourceException on the first string read.
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // web breadth: the target compiles — uniformity with the other
    // web modules — but the live player itself stays Android-only. The
    // commonMain surface (ViewModel, engine seams, UI-state, navigation
    // vocabulary) now compiles for wasm: its only java.time cluster
    // (Instant/DateTimeFormatter.ISO_INSTANT in the program-window fetch)
    // ported to kotlin.time (toString() renders the identical ISO-8601 UTC
    // instant; parse is a superset — see the VM comment), and every core:data
    // dep it names (MediaRepository/PlaybackRepository/LiveTvRepository,
    // TranscodeReasonsRefresher, ImageUrlProvider, the datastore stores) has
    // been commonMain since. The actual media surface — the screen
    // (media3 PlayerView), ExoLiveEngine + its OkHttp data source, the
    // D-pad seek bar — lives in androidMain and has no web artifact, so
    // Route.LiveTvChannelPlayer stays unrouted on web AND latent on desktop
    // (the documented desktop dead-end). The web graph therefore compiles a
    // module whose only live surface is the shared player logic; wiring a
    // web live player needs an engine seam first. The karma/Chrome browser
    // run stays off like core:ui/core:network — jvmTest pins the semantics.
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }
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

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (the
// identical block lives in shared/core/ui, shared/feature/requests and the
// other web modules).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}
