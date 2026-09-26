package com.raulshma.jellyplay.feature.player.live.engine

/**
 * The live mute-memory chip — the ONE pre-mute remember/restore policy for
 * the live player's mute toggle. Extracted from the [LiveTvPlayerViewModel]'s
 * former private `preMuteVolume: Float?`, which was written inline across
 * toggleMute and stop() with the semantics living only in comments.
 *
 * Why a dedicated policy instead of the VOD player's
 * `PlaybackVolumePolicy.planMute/planUnmute`: that core models a richer
 * surface than live has, and adopting it would CHANGE live's behavior —
 * its `planUnmute` floors the remembered level at 0.05 (live restores the
 * exact pre-mute level, 0.03 stays 0.03), its plans carry a system
 * music-stream value (live has no such seam — [LivePlayerAudio] is a bare
 * player-volume float), and its unmute takes a non-null remembered level
 * (live's chip-null means "leave the current volume untouched"). Live's
 * semantics are deliberate and test-pinned, so they get their own chip.
 *
 * The transitions, verbatim from the former inline arms:
 *  - muting snapshots the raw player volume read BEFORE the mute write, so
 *    unmute restores exactly what was playing — never a fixed default;
 *  - unmuting restores that snapshot, or NOTHING when the chip is empty
 *    (the mute was set externally, or the player was swapped between the
 *    arms), then clears the chip;
 *  - stop()/teardown clears the chip so a stale level is never restored
 *    onto a LATER player (mute → leave screen → return to a fresh engine).
 *
 * Internal: consumed only by the ViewModel (the applier) and this module's
 * jvmTest — not a stable API surface.
 */
internal data class LiveMuteMemory(val preMuteVolume: Float? = null) {

    /**
     * Muting: remember [currentVolume] — the raw player level the caller
     * read BEFORE writing the mute. Overwrites unconditionally, so a re-mute
     * always captures the current player's level.
     */
    fun onMute(currentVolume: Float): LiveMuteMemory = copy(preMuteVolume = currentVolume)

    /**
     * Unmute decision: the pre-mute level to restore, or `null` = leave the
     * current volume untouched (never slam to a fixed default).
     */
    fun restorationVolume(): Float? = preMuteVolume

    /** Unmuting clears the chip — a stale level must never outlive its mute. */
    fun onUnmute(): LiveMuteMemory = LiveMuteMemory(null)

    /**
     * stop()/teardown: drop the remembered level so it can never be restored
     * onto a fresh engine on a later screen entry.
     */
    fun onStopped(): LiveMuteMemory = LiveMuteMemory(null)
}
