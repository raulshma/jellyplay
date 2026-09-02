package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio

/**
 * Deep module: derives the player's aspect-ratio mode from a media item's
 * video stream dimensions. Previously this pure logic lived inline as a private
 * function on the 2958-LOC [VideoPlayerViewModel], where it could not be unit-
 * tested through the VM's interface. Extracted so the threshold logic has a
 * home and a direct test surface.
 *
 * Threshold rationale (matches the aspect-ratio picker's display values):
 *   - ≥ 2.3   → 21:9 (cinemascope)
 *   - ≥ 1.7   → 16:9 (widescreen HD / most streaming)
 *   - ≥ 1.3   → 4:3  (classic TV)
 *   - below   → FIT  (let the engine fit the frame; phone-portrait, etc.)
 */
internal fun detectAspectRatio(streams: List<MediaStream>): AspectRatio? {
    val videoStream = streams.firstOrNull { it.type == StreamType.VIDEO } ?: return null
    val width = videoStream.width ?: return null
    val height = videoStream.height ?: return null
    if (height == 0) return null

    val nativeRatio = width.toFloat() / height.toFloat()
    return when {
        nativeRatio >= 2.3f -> AspectRatio.RATIO_21_9
        nativeRatio >= 1.7f -> AspectRatio.RATIO_16_9
        nativeRatio >= 1.3f -> AspectRatio.RATIO_4_3
        else -> AspectRatio.FIT
    }
}
