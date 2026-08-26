package com.raulshma.jellyplay.feature.player.video

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider

/**
 * Android-typed API adapters over the commonMain [VideoPlayerViewModel]
 * surface (wave 8C): the screen's call sites keep their android.net.Uri /
 * android.graphics signatures unchanged — each adapter stringifies or
 * re-packs the argument and forwards to the common member. Declared as
 * extensions (same package) so overload resolution picks them without any
 * import at the call sites.
 */

/**
 * PiP source-rect hint: a null rect previously nulled the stored hint; the
 * screen always passes a real rect, so null is a no-op here (documented
 * divergence — nothing calls it with null).
 */
fun VideoPlayerViewModel.updatePipSourceRect(rect: Rect?) {
    rect?.let { updatePipSourceRect(it.left, it.top, it.right, it.bottom) }
}

/** Trickplay thumbnail narrowed back to the platform bitmap. */
suspend fun VideoPlayerViewModel.getTrickplayThumbnail(positionMs: Long): Bitmap? =
    loadTrickplayThumbnail(positionMs) as? Bitmap

/** SAF-picked font install: the Uri is stringified at the seam boundary. */
fun VideoPlayerViewModel.installUserFont(uri: Uri) = installUserFont(uri.toString())

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

/** SAF-picked side-load: the Uri is stringified at the seam boundary. */
internal fun SubtitleManager.addLocalSubtitle(uri: Uri, fileName: String) =
    addLocalSubtitle(uri.toString(), fileName)

/** SAF-picked upload: the Uri is stringified at the seam boundary. */
internal fun SubtitleManager.uploadSubtitle(
    uri: Uri,
    fileName: String,
    language: String?,
    isForced: Boolean,
    isHearingImpaired: Boolean,
) = uploadSubtitle(uri.toString(), fileName, language, isForced, isHearingImpaired)
