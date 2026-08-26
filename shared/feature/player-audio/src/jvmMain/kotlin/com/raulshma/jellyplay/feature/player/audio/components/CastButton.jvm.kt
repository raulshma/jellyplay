package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast

/**
 * Desktop actual: renders nothing. Cast discovery + the device picker are
 * Android-only (the CastManager cluster is Hilt-owned in legacy :core:data);
 * Route.AudioPlayer is guarded in DesktopAppRoot so this button never mounts
 * on desktop v1. If the player ever goes live there, the real fix is a
 * desktop [AudioPlayerCast] def, not a dialog here.
 */
@Composable
actual fun CastButton(castController: AudioPlayerCast) {
    // No-op: unreachable on desktop (guarded route).
}
