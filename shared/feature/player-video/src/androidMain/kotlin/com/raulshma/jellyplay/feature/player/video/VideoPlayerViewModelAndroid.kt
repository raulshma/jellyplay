package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider

/**
 * Android-typed view over the commonMain [VideoPlayerViewModel] surface
 * (wave 8C). Wave 9A moved the player screen to commonMain behind the
 * platform seams, so the former android.net.Uri / android.graphics adapter
 * extensions (updatePipSourceRect(Rect?), getTrickplayThumbnail,
 * installUserFont(Uri), SubtitleManager.addLocalSubtitle/uploadSubtitle Uri
 * overloads) lost their last callers and were folded away — the screen now
 * consumes the stringified/Int-typed common members directly. What remains
 * are the legacy-singleton views the VideoPlayerScreenSeams android actuals
 * (cast chrome, mpv subtitle overlay) still need.
 */

/**
 * The legacy cast surface (discovery/connect, Context-bound transport) the
 * screen's chrome needs; the commonMain VM property is the narrow seam
 * interface. Same singleton the adapter wraps.
 */
val VideoPlayerViewModel.androidCastManager: com.raulshma.jellyplay.core.data.cast.CastManager
    get() = (castManager as AndroidCastManager).delegate

/** The Android-only font surface (Typeface cache, libass dir) for the overlay. */
val VideoPlayerViewModel.androidFontProvider: AndroidFontProvider
    get() = fontProvider as AndroidFontProvider

/**
 * The cast slice's Android-only members (`disconnect(context)`, the legacy
 * CastSessionEvent flow) that stay off the commonMain interface.
 */
internal val VideoPlayerViewModel.androidCast: AndroidPlayerCastController
    get() = cast as AndroidPlayerCastController
