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
        namespace = "com.raulshma.jellyplay.shared.feature.player.video"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target yet (same as every shared/feature module): the web
    // shell lands in plan §Phase W; android+jvm covers V3/Phase X consumers.
    // This also keeps java.* legal in commonMain — same precedent as
    // shared/core:data and :feature:syncplay (the track-scoring / trickplay
    // helpers use java.io.File, the seek bar java.text.SimpleDateFormat).
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
            // Repository interfaces + playback state holders the screens,
            // sheets and controllers read (MediaRepository, PlaybackRepository,
            // OfflinePlaybackFacade, PlaybackSourceResolver, EpisodeCatalogue,
            // SyncPlayManager, AdaptiveBitrateManager, … all commonMain/
            // jvmShared since the Phase C4 flips).
            implementation(project(":shared:core:data"))
            // 10 DataStore slices + VideoPlayerAggregateStore projection.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // MediaEngine contract + engine value types (AspectRatio,
            // EnginePlaybackState, SegmentCalculator, SubtitleSource, …) —
            // replaces the legacy :feature:player:core api edge, which this
            // module absorbed (wave 7C).
            implementation(project(":shared:core:player-contract"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API +
            // the suspend getString resolver the VideoStrings seam uses).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 and
            // hilt-navigation-compose edges were dropped with the move: the
            // screen hosts no nav entry — PlayerActivity is the sole entry
            // point — and the ViewModel is Koin-owned via koinViewModel.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            // SavedStateHandle in PlaybackSession/VideoPlayerViewModel (KMP
            // since lifecycle 2.9 — StudioDetailViewModel precedent).
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.savedstate)
            // collectAsStateWithLifecycle in the screens/sheets.
            implementation(libs.lifecycle.runtime.compose)
            // Coil for the next-episode / companion-dashboard artwork.
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.core)
            // androidx.collection.LruCache memoization in VttTagParser
            // (KMP artifact — shared/core:data precedent).
            implementation(libs.androidx.collection)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // Wave 7C shape (subtitle-tester androidMain-heavy precedent, one
        // notch further): every media3/libmpv/libVLC/cast type, the engine
        // stack, the Context+Uri session/subtitle managers, the media-session
        // + screenshot + trickplay controllers AND the monolith
        // VideoPlayerViewModel/VideoPlayerScreen pair live here. The pure
        // policy/scoring/state/sheet chrome is commonMain; the Android host
        // surface (PlayerActivity) reaches this module's androidMain only.
        // Desktop is deliberately latent: no registration, no nav route —
        // the players stay guarded in DesktopAppRoot.
        // Wave 8C: the desktop DI module (desktopPlayerVideoModule) registers
        // the now-commonMain VideoPlayerViewModel plus the jvmMain seam stubs.
        // No media3/legacy deps here — jvmMain sees only commonMain's deps.
        // Wave 9A: + JNA for the desktop EngineVideoSurface actual, which
        // resolves the embedded child window's HWND (Native.getComponentPointer,
        // core artifact — no jna-platform). The Android target never sees this
        // edge; apps/desktop already ships libs.jna at runtime for libmpv, so
        // it carries no new runtime weight. Kept in this module because the
        // surface host is the co-module actual of an internal expect — moving
        // it app-side would force the seam public or add an indirection layer.
        getByName("jvmMain").dependencies {
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jna)
            // SwingPanel host for the EngineVideoSurface desktop actual — the common
            // window package reaches jvmMain through jb-compose-ui, but SwingPanel is
            // desktop-only and needs this explicit edge.
            implementation(libs.jb.compose.ui.desktop)
        }
        getByName("androidMain").dependencies {
            // Documented shared→legacy edges (library/livetv/admin/settings/
            // subtitle-tester precedent; dies at Phase X): the Koin factory
            // adapts the Hilt-owned legacy playback singletons
            // (PlaybackSessionManager, CastManager,
            // JellyfinRemotePlayCastStrategy, ActivePlayerController) and
            // the legacy UserMessageBus. The jvm target NEVER sees this edge.
            implementation(project(":core:data"))
            implementation(project(":core:ui"))
            // media3 engine stack (ExoPlayer engine + cast + session +
            // cache + extractors + FFmpeg extension decoder).
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.media3.session)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.media3.exoplayer.dash)
            implementation(libs.media3.extractor)
            implementation(libs.media3.cast)
            implementation(libs.media3.datasource)
            implementation(libs.media3.datasource.okhttp)
            // StandaloneDatabaseProvider backs the video SimpleCache's index
            // (VideoStreamCache), mirroring the audio cache in :core:data.
            implementation(libs.media3.database)
            // FFmpeg software audio decoder for codecs the platform can't
            // decode (DTS, MLP/TrueHD, EAC3, etc.). DefaultRenderersFactory
            // loads it via reflection when EXTENSION_RENDERER_MODE is ON (the
            // default HW_PREFERRED decoder mode), so no engine wiring is
            // needed.
            implementation(libs.media3.ffmpeg.decoder)
            implementation(libs.play.services.cast.framework)
            implementation(libs.mediarouter)
            implementation(libs.okhttp)
            implementation(libs.libmpv)
            // libass wired into Media3's renderer pipeline — full ASS/SSA
            // rendering (positioning, fonts, animations, karaoke, vector
            // drawing) on the ExoPlayer backend. Bundles its own libass
            // native .so per ABI.
            implementation(libs.ass.media)
            // Direct compile dep on ass-kt so ExoPlayerEngine can reach
            // AssRender.setFontScale (was only a transitive runtime dep
            // before; SCALE font-size override needs it).
            implementation(libs.ass.kt)
            // Parse font family names from .ttf/.otf headers for the
            // subtitle font picker.
            implementation(libs.truetype.parser)
            implementation(libs.libvlc.all)
            // Koin owns the ViewModel + engine stack (V3 feature conveyor:
            // one framework per type — the Hilt annotations were stripped at
            // the move; the app-side interop singles flipped direction, see
            // HiltInteropModule).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy
// :feature:player:video so migrated files keep their
// `com.raulshma.jellyplay.feature.player.video` imports; generated accessors
// land in `...feature.player.video.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.player.video.generated.resources"
