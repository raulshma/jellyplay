package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * The remote-control face over the Android audio family — the transport,
 * queue, volume and mode verbs the remote "Play"/"Playstate"/"GeneralCommand"
 * router ([com.raulshma.jellyplay.core.data.remote.AndroidAudioRemoteControlDispatcher])
 * drives, plus the one position read its Rewind/FastForward arms take. One
 * role per face (the [ShellAudioSurface] role-split idiom): exactly what that
 * dispatcher reads, nothing else.
 *
 * Satisfied directly by [AudioPlaybackManager] (every member is an existing
 * public method or override); the Koin alias rides androidPlaybackStackModule
 * beside the other interface aliases — ONLY the DI module names the concrete
 * class. Declared androidMain because the Android manager is the only
 * implementation and the dispatcher its only consumer (the desktop twin's
 * dispatcher drives [com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager]
 * through its own wiring).
 */
interface AudioRemoteControlSurface {
    /** The published playback position — the Rewind/FastForward offset base. */
    val currentPosition: StateFlow<Long>

    /** Play a single item (single-item remote "Play"). */
    fun play(itemId: String)

    /** Build + start a queue (multi-item remote "Play"). */
    fun playQueue(items: List<AudioQueueItem>, startIndex: Int)

    /** Terminal stop — tear the engine down (Jellyfin's "Stop"). */
    fun stopAndRelease()

    fun pause()

    /** Resume (the Playstate "Unpause" — the manager's concrete resume verb). */
    fun resume()

    fun togglePlayPause()

    fun seekTo(positionMs: Long)

    fun skipToNext()

    fun skipToPrevious()

    /** The USER volume path (mirrors the system stream; the remote "SetVolume"). */
    fun setVolume(volume: Float)

    fun setMuted(muted: Boolean)

    fun increaseVolume()

    fun decreaseVolume()

    fun toggleMute()

    /** 0 = RepeatNone, 1 = RepeatAll, 2 = RepeatOne (the "SetRepeatMode" payload). */
    fun setRepeatMode(mode: Int)

    fun setShuffleMode(enabled: Boolean)
}
