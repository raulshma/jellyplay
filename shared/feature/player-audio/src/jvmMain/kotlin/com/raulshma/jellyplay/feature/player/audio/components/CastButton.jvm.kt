package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast

/**
 * Desktop actual: renders nothing. Route.AudioPlayer went live on desktop
 * with the wave-9B real audio core (this button now mounts), but cast
 * discovery + the device picker remain Android-only — playback routes through
 * the never-connected DesktopAudioPlayerCast def in desktopPlayerModule.
 */
@Composable
actual fun CastButton(castController: AudioPlayerCast) {
    // No-op: desktop has no Cast devices to discover.
}
