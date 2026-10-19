package com.raulshma.jellyplay.feature.player.video.engine

/**
 * The per-frame `time-pos` dispatch dedupe (the MpvPlayerEngine property
 * observer's gate). Holds the last ACCEPTED time-pos (seconds); null = the
 * next observation lands unthrottled.
 *
 * The window is half of SyncPlay's 100 ms correction threshold — the cached
 * playhead may lag the demuxer by at most this window plus one frame. The
 * delta is absolute: a discontinuity (seek) always exceeds the window and
 * dispatches; only the sub-window per-frame creep is skipped. [reset] runs on
 * every state change that can move the playhead discontinuously
 * (seek/pause/play/stop/load/track change) so the first observation after one
 * of them dispatches.
 */
internal class TimePosDedupe {

    private var lastAcceptedSec: Double? = null

    /** True = dispatch the observation; false = dedupe it (moved < [TIME_POS_DEDUPE_SEC]). */
    fun accept(seconds: Double): Boolean {
        val last = lastAcceptedSec
        if (last == null || kotlin.math.abs(seconds - last) >= TIME_POS_DEDUPE_SEC) {
            lastAcceptedSec = seconds
            return true
        }
        return false
    }

    fun reset() {
        lastAcceptedSec = null
    }

    companion object {
        internal const val TIME_POS_DEDUPE_SEC = 0.05
    }
}
