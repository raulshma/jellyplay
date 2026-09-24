import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.player.video"
        // Rehomed :app androidTest suites (AVSyncSheet, AspectRatioSheet,
        // DecoderPickerSheet, HdrBadge, NextEpisodeOverlay, PlaybackInfoOverlay,
        // SkipOverlay, SubtitleDelayOverlay, SubtitleManagerSection,
        // SubtitleStyleSheet, TrickplayOverlay) — withHostTest creates the
        // androidUnitTest variant bound to the Kotlin test tree (AGP-9 KMP
        // library plugin). Flags mirrored verbatim from shared/core/ui: real
        // resource serving for compose-resources string lookups and
        // unstubbed-Context tolerance.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // This module's commonMain legitimately carries java.* (the
        // track-scoring / trickplay helpers use java.io.File, the seek bar
        // java.text.SimpleDateFormat), so it compiles for android+jvm only.
        // Both targets and the default hierarchy come from the convention
        // plugin.

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            // Repository interfaces + playback state holders the screens,
            // sheets and controllers read (MediaRepository, PlaybackRepository,
            // OfflinePlaybackFacade, PlaybackSourceResolver, EpisodeCatalogue,
            // SyncPlayManager, AdaptiveBitrateManager, … all commonMain/
            // jvmShared since the flips).
            implementation(project(":shared:core:data"))
            // 10 DataStore slices + VideoPlayerAggregateStore projection.
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // MediaEngine contract + engine value types (AspectRatio,
            // EnginePlaybackState, SegmentCalculator, SubtitleSource, …) —
            // replaces the legacy :feature:player:core api edge, which this
            // module absorbed.
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
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
            // Desktop compose UI test for the keyboard-focus grab
            // (PlayerKeyboardFocusGrabUiTest) — runComposeUiTest is the
            // framework-agnostic ComposeUiTest entry (no JUnit4 runner; the
            // suite stays on kotlin-test like the rest of jvmTest).
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            // The UI test needs a real skia scene: currentOs pulls the
            // skiko-awt runtime (native lib) the ui-test scene renders with.
            implementation(compose.desktop.currentOs)
            // Scratch Robot repro (DesktopSheetWheelRobotTest) drives a real
            // ComposeWindow — that AWT window type lives in ui-desktop.
            implementation(libs.jb.compose.ui.desktop)
        }
        // Subtitle-tester's androidMain-heavy shape taken one notch
        // further: every media3/libmpv/libVLC/cast type, the engine
        // stack, the Context+Uri session/subtitle managers, the media-session
        // + screenshot + trickplay controllers AND the monolith
        // VideoPlayerViewModel/VideoPlayerScreen pair live here. The pure
        // policy/scoring/state/sheet chrome is commonMain; the Android host
        // surface (PlayerActivity) reaches this module's androidMain only.
        // Desktop is registered and live: desktopPlayerVideoModule sits in
        // apps/desktop's Koin graph and DesktopAppRoot hosts
        // entry<Route.VideoPlayer> (mpv engine behind its platform support
        // guard).
        // The desktop DI module (desktopPlayerVideoModule) registers
        // the now-commonMain VideoPlayerViewModel plus the jvmMain seam stubs.
        // No media3/legacy deps here — jvmMain sees only commonMain's deps.
        // + JNA for the desktop EngineVideoSurface actual, which
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
            // The Koin factory adapts the platform playback singletons
            // (PlaybackSessionManager, CastManager,
            // JellyfinRemotePlayCastStrategy, ActivePlayerController —
            // shared:core:data androidMain since the cutover) and
            // the UserMessageBus (:shared:core:ui androidMain). The jvm
            // target NEVER sees this edge.
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
// feature:player:video so migrated files keep their
// `com.raulshma.jellyplay.feature.player.video` imports; generated accessors
// land in `...feature.player.video.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.player.video.generated.resources"

// Robolectric lane for the 11 rehomed :app androidTest Compose suites.
// AGP 9.4's withHostTest names the lane's source set androidHostTest
// (src/androidHostTest/kotlin) and materializes it only in afterEvaluate, so
// the dependency wiring rides a configureEach — an eager lookup would run
// before the source set exists (same pattern as shared/core/ui).
kotlin.sourceSets.configureEach {
    if (name == "androidHostTest") {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            // Compose UI tests under Robolectric (the rehomed sheet/overlay
            // regression suites; media3's @UnstableApi opt-in rides the
            // androidMain runtime classpath).
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}
