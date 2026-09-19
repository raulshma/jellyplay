package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast

/**
 * Web actual: renders nothing — the desktop actual's shape. Cast discovery +
 * the device picker are Android-only platform surfaces; the web shell has no
 * cast backend either, so the entry point stays hidden.
 */
@Composable
actual fun CastButton(castController: AudioPlayerCast) {
    // No-op: no Cast devices to discover in a browser tab.
}
