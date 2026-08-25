package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform seams for the detail feature (V3/Phase X conveyor move from
 * `:feature:details`, which reached three Hilt-owned Android-only singletons
 * and one Intent share target).
 *
 * One-framework-per-type: the moved ViewModels are Koin-constructed, so every
 * collaborator that used to be a Hilt-injected Android class arrives through
 * one of these module-local interfaces instead:
 *
 * - [DetailAudioPlayback] — legacy `AudioPlaybackManager` (ExoPlayer/media3,
 *   Hilt-owned in legacy `:core:data` until Phase X). The detail screen's ONLY
 *   use of the manager is per-item `play(itemId)` with its local-source
 *   fallback (`playLocalTrack`).
 * - [DetailThemeMusic] — legacy `ThemeMusicPlayer` (a dedicated ExoPlayer
 *   instance for ambient detail-page theme music; same Hilt singleton).
 * - [DetailStorageProbe] — the `StatFs`/`Environment` available-bytes probe
 *   that used to live inline in [DetailViewModel.getAvailableStorageBytes].
 *
 * Android actuals for the first two live APP-side
 * (`HiltInteropModule`, EntryPointAccessors — the same lazy interop singles
 * as the subtitle-tester player engine) because constructing the singletons
 * here would mean a second framework per type. [DetailStorageProbe]'s Android
 * impl is plain `android.os` API and lives in this module's androidMain;
 * desktop impls (no-op audio/theme, appdata usable-space probe) live in
 * jvmMain.
 */
interface DetailAudioPlayback {
    /**
     * Starts local-capable playback of a single (audio) item — the legacy
     * `AudioPlaybackManager.play(itemId)` local-track path including the
     * `resolveLocalSource` fallback when the server detail fetch fails.
     * Desktop v1: silent no-op (audio playback goes through the music queue
     * facade there; the detail-screen audio buttons are dead-clicks,
     * documented).
     */
    fun play(itemId: String)
}

interface DetailThemeMusic {
    /** Plays ambient theme music for [itemId] when the pref is on; no-op guard rules live in the impl. */
    fun playThemeFor(itemId: String)

    /** Stops and releases any playing theme music. */
    fun stop()
}

interface DetailStorageProbe {
    /**
     * Available bytes on the volume backing the download destination
     * (music subtree for audio, movies otherwise) — the legacy
     * `StatFs`-against-`getExternalFilesDir` probe on Android; the appdata
     * downloads volume's `File.getUsableSpace()` on desktop
     * (DesktopDownloadStorageLayout precedent).
     */
    suspend fun availableBytes(isAudio: Boolean): Long
}

/**
 * Composable share action for the `jellyplay://media/<id>` deep link
 * (legacy `Intent.ACTION_SEND` chooser). Android actual = the verbatim
 * chooser body with the title pre-resolved by the caller; desktop actual =
 * no-op (the share menu entry is a documented dead-click, subtitle-tester
 * settings-row precedent).
 */
@Composable
internal expect fun rememberShareMediaAction(itemId: String, chooserTitle: String): () -> Unit

/**
 * In-app YouTube trailer embed host (legacy core:ui's WebView iframe player).
 * Android actual delegates to that composable verbatim (this module's
 * androidMain → legacy `:core:ui` edge, library/livetv/admin/calendar
 * messenger precedent — dies at Phase X); desktop actual fires
 * [onEmbedFailed] immediately so every call site degrades through the SAME
 * fallback Android uses when the WebView embed breaks (open the browser /
 * hide the autoplay overlay) — no desktop code path reaches a stuck black
 * dialog.
 */
@Composable
internal expect fun InlineTrailerPlayerHost(
    videoKey: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    showControls: Boolean = true,
    autoplay: Boolean = true,
    focusable: Boolean = true,
    cropToFill: Boolean = false,
    onEmbedFailed: () -> Unit = {},
)
