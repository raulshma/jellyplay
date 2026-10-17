package com.raulshma.jellyplay.feature.player.video.engine.mpv

import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState

/**
 * What the watchdog wants the host to do about a detected playback stall.
 */
sealed interface MpvStallRecoveryAction {
    /** Where the recovery seek lands — the frozen position + the forward slack. */
    val kickToMs: Long

    /**
     * Absolute seek-restart at the frozen position — the proven play-loop
     * kick. mpv processes the seek command even while wedged, and the
     * playback restart it triggers re-opens the audio chain (a failed
     * spdif→PCM fallback completes with the PCM format) and re-presents
     * frames. Verified live on the repro device: a stuck-at-0:00 session
     * (1080p SDR, copy decode) resumed video + audio the moment a seek
     * landed.
     */
    data class Kick(
        override val kickToMs: Long,
    ) : MpvStallRecoveryAction

    /**
     * Write `hwdec` first, then the kick — the direct zero-copy path is
     * suspect. The runtime `hwdec` write reselects the current video
     * decoder, so the kick's restart re-decodes via the copy path into
     * system-memory frames the VO can always present (verified live: the
     * 4K DV direct path wedged with `acquireLatestImage failed`, and the
     * copy path rendered the same file correctly tone-mapped).
     */
    data class SwitchHwdecThenKick(
        val hwdecValue: String,
        override val kickToMs: Long,
    ) : MpvStallRecoveryAction
}

/**
 * Detects mpv's silent "position frozen while claiming to play" wedges and
 * decides the recovery action for them.
 *
 * The repro that forced this: 4K Dolby Vision content on an Adreno 509-class
 * device (Nokia 6.1 Plus / Android 14) under the DEFAULT `mediacodec`
 * zero-copy hwdec. mpv decodes fine, `vo/gpu-next` reconfigures, the first
 * frame is announced — then the AImageReader acquisition fails
 * (`acquireLatestImage failed: -30001`), no frame ever reaches the GL
 * consumer, and the play loop sits wedged forever: black screen, position
 * frozen at 0:00, no UI error of any kind. A second, independent wedge showed
 * itself with copy decode too: when the spdif passthrough request fails on a
 * speaker-only sink at first load, the "Falling back to PCM output" path can
 * leave the play loop parked at 0:00 until a seek kick restarts it.
 *
 * The common observable for both wedges is precise: `playbackState == READY`,
 * the fold says we are playing (not paused, not buffering), and the position
 * does not move across ticks. Recovery is the seek kick above, escalating to
 * the hwdec switch when the zero-copy path is implicated. The host applies
 * the returned action (property write + `core.seekTo`); this class owns only
 * WHEN and WHICH.
 *
 * Timing: a window opens on the first tick that sees the same position as the
 * previous one and fires once it has stayed frozen [STALL_CONFIRM_MS] — for a
 * session wedged from birth (1s ticks) the first fire lands at t=10s (the
 * -1 position seed makes the first tick read as progress), the second (the
 * unconditional switch) at t=18s. Each fire re-anchors the window; real
 * progress closes it and restores the attempt budget.
 *
 * False-positive cost is low by construction: a kick seeks to the current
 * position + [KICK_FORWARD_MS], which a healthy-but-slow session would
 * barely notice, and the state gate excludes paused / buffering / idle /
 * ended sessions. Attempt budget is capped so a genuinely unplayable file
 * does not turn into an endless seek loop — after [MAX_ATTEMPTS] the
 * watchdog stands down (the shipped failure mode returns, which is at least
 * the pre-watchdog status quo).
 */
class MpvStallWatchdog {

    /** Position observed on the previous tick — the stall detector's baseline. */
    private var lastPositionMs: Long = -1L

    /** First tick the position was seen frozen while playing; 0 = no stall window. */
    private var stallSinceMs: Long = 0L

    /** Recovery actions issued for the current stall streak. */
    private var attempts: Int = 0

    /** New-file load: window, budget and the position seed all start fresh. */
    fun reset() {
        lastPositionMs = -1L
        stallSinceMs = 0L
        attempts = 0
    }

    /**
     * One position-ticker tick. Returns the recovery action to execute, or
     * null while the session looks healthy (or the budget is exhausted).
     *
     * @param aimagereaderFailureObserved the host's log-detector flag: an
     *   `[vo/gpu-next/aimagereader]` acquire failure was seen for this file,
     *   implicating the direct zero-copy hwdec path.
     */
    fun onTick(
        nowMs: Long,
        playbackState: EnginePlaybackState,
        isPlaying: Boolean,
        positionMs: Long,
        aimagereaderFailureObserved: Boolean,
    ): MpvStallRecoveryAction? {
        if (playbackState != EnginePlaybackState.READY || !isPlaying) {
            // Paused / buffering / idle / ended — not the wedged-prone steady
            // state. Reset the window; keep the attempt budget (a pause +
            // resume cycle around an unrescued wedge must not re-arm it
            // unboundedly).
            stallSinceMs = 0L
            lastPositionMs = positionMs
            return null
        }
        if (positionMs != lastPositionMs) {
            // Real progress (or any seek/restart write-through) — fresh
            // window, fresh budget.
            lastPositionMs = positionMs
            stallSinceMs = 0L
            attempts = 0
            return null
        }
        if (stallSinceMs == 0L) stallSinceMs = nowMs
        if (nowMs - stallSinceMs < STALL_CONFIRM_MS) return null
        if (attempts >= MAX_ATTEMPTS) return null
        attempts++
        // Re-anchor at the fire: the NEXT attempt (if this one fails too)
        // needs another full confirm window — two kicks can never fire
        // back-to-back.
        stallSinceMs = nowMs
        val kickToMs = positionMs + KICK_FORWARD_MS
        return when {
            aimagereaderFailureObserved && attempts == 1 ->
                MpvStallRecoveryAction.SwitchHwdecThenKick(
                    hwdecValue = HWDEC_STALL_FALLBACK,
                    kickToMs = kickToMs,
                )
            attempts == 2 -> MpvStallRecoveryAction.SwitchHwdecThenKick(
                hwdecValue = HWDEC_STALL_FALLBACK,
                kickToMs = kickToMs,
            )
            else -> MpvStallRecoveryAction.Kick(kickToMs)
        }
    }

    companion object {
        /** A wedged session has been confirmed once the position is frozen this long. */
        const val STALL_CONFIRM_MS = 8_000L

        /** The kick seeks this far past the frozen position — invisible to the user. */
        const val KICK_FORWARD_MS = 250L

        /** Kick (+ switch) attempts before the watchdog stands down. */
        const val MAX_ATTEMPTS = 2

        /**
         * The hwdec value the switch writes. The copy path decodes through
         * MediaCodec's Image API into system-memory frames — slower per frame
         * than zero-copy, but it renders on every driver that can present GL
         * textures at all, which is exactly what a wedged zero-copy path
         * cannot.
         */
        const val HWDEC_STALL_FALLBACK = "mediacodec-copy"
    }
}

/**
 * The host-side mpv log detector feeding [MpvStallWatchdog.onTick]'s
 * `aimagereaderFailureObserved` flag. Matches the failure signatures the
 * wedged repro produced (kept narrow on the prefix so unrelated
 * "Waiting for ..." logs never match).
 */
fun isAimagereaderFailureLog(prefix: String, text: String): Boolean =
    prefix.contains("aimagereader") &&
        (
            text.contains("acquireLatestImage failed") ||
                text.contains("Waiting for frame timed out")
            )
