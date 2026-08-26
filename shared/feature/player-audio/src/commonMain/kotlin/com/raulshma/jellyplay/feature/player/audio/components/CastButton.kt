package com.raulshma.jellyplay.feature.player.audio.components

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.feature.player.audio.AudioPlayerCast

/**
 * Cast / "Play On" entry point for the audio player (wave 7A conveyor move
 * from `:feature:player:audio`). expect/actual because the device picker is a
 * platform `android.app.AlertDialog` on Android (the verbatim legacy picker,
 * now driven through the [AudioPlayerCast] seam instead of the concrete
 * CastManager); the desktop actual renders nothing — Route.AudioPlayer is
 * live on desktop since wave 9B, but no cast backend exists there (the
 * desktop [AudioPlayerCast] def is a never-connected no-op), so the button
 * hides its entry point.
 */
@Composable
expect fun CastButton(castController: AudioPlayerCast)
