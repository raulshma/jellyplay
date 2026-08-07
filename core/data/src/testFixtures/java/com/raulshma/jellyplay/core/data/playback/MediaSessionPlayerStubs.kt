@file:JvmName("MediaSessionPlayerStubs")

package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk

/**
 * Builds a relaxed `mockk<Player>` with every getter [MediaSession] reads during
 * construction + its PlaybackStateCompat projection pinned to a non-null value.
 *
 * `MediaSession.Builder` posts a bundle-building callback that NPEs when mockk
 * returns null for the Player's boxed return types (`currentPosition`, `duration`,
 * `playbackParameters`, …). Both PlaybackSessionManagerPriorityTest and
 * MediaSessionControllerTest need this identical stubbing, so it lives here in
 * :core:data testFixtures and is shared across modules.
 *
 * @param isPlaying shapes `isPlaying`, `playWhenReady`, and `playbackState`
 *  (READY when playing, IDLE otherwise) to exercise the session manager's
 *  priority guard exactly as production does.
 */
fun stubMediaSessionPlayer(isPlaying: Boolean = false): Player {
    val player = mockk<Player>(relaxed = true)
    every { player.canAdvertiseSession() } returns true
    every { player.applicationLooper } returns android.os.Looper.getMainLooper()
    every { player.isPlaying } returns isPlaying
    every { player.playbackState } returns if (isPlaying) Player.STATE_READY else Player.STATE_IDLE
    every { player.currentPosition } returns 0L
    every { player.bufferedPosition } returns 0L
    every { player.duration } returns C.TIME_UNSET
    every { player.contentPosition } returns 0L
    every { player.contentBufferedPosition } returns 0L
    every { player.contentDuration } returns C.TIME_UNSET
    every { player.playbackParameters } returns PlaybackParameters.DEFAULT
    every { player.currentMediaItem } returns null
    every { player.currentMediaItemIndex } returns 0
    every { player.playWhenReady } returns isPlaying
    every { player.playbackSuppressionReason } returns 0
    every { player.playerError } returns null
    every { player.repeatMode } returns Player.REPEAT_MODE_OFF
    every { player.shuffleModeEnabled } returns false
    return player
}
