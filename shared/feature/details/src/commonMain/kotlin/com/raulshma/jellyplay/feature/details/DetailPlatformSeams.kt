package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform seams for the detail feature (V3/ conveyor move from
 * `:feature:details`, which reached three Hilt-owned Android-only singletons
 * and one Intent share target).
 *
 * One-framework-per-type: the moved ViewModels are Koin-constructed, so every
 * collaborator that used to be a Hilt-injected Android class arrives through
 * one of these module-local interfaces instead:
 *
 * - [DetailThemeMusic] — legacy `ThemeMusicPlayer` (a dedicated ExoPlayer
 *   instance for ambient detail-page theme music; same Hilt singleton).
 * - [DetailStorageProbe] — the `StatFs`/`Environment` available-bytes probe
 *   that used to live inline in [DetailViewModel.getAvailableStorageBytes].
 *
 * ([DetailAudioPlayback] — the legacy per-item `AudioPlaybackManager.play`
 * seam — died with its only caller, the production-unreachable local-track
 * play command; album/track playback from the detail screen routes through
 * AudioQueueFacade.)
 *
 * Android actuals for [DetailThemeMusic] live APP-side
 * (`AppKoinModule`'s interop adapter over the Koin-owned legacy
 * ThemeMusicPlayer single — formerly the HiltInterop lazy single) because
 * constructing the singleton here would mean a second module boundary per
 * type. [DetailStorageProbe]'s Android impl is plain `android.os` API and
 * lives in this module's androidMain; desktop impls (no-op theme music,
 * appdata usable-space probe) live in jvmMain.
 */
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
 * messenger precedent — dies at ); desktop actual fires
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
