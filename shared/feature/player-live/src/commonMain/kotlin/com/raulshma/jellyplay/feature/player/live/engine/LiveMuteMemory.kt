package com.raulshma.jellyplay.feature.player.live.engine

/**
 * The live mute-memory chip — the ONE pre-mute remember/restore store for
 * the live player's mute, consumed by [LiveMuteController] (the engine-side
 * host of the shared volume/mute template). The former ViewModel applier
 * died when mute became real engine state; the chip keeps only the
 * exact-level remember/restore semantics live declares ON that shared
 * template call — the restore carries no audible floor (0.03 stays 0.03; the
 * template's `unmuteRestoreFloor` is 0 on live), the empty chip maps to the
 * template's LEAVE_UNCHANGED native restore ("leave the current volume
 * untouched"), and there is no system music-stream value.
 *
 * The transitions, verbatim from the former inline arms:
 *  - muting snapshots the raw player volume read BEFORE the mute write, so
 *    unmute restores exactly what was playing — never a fixed default;
 *  - the controller clears the chip after applying an unmute, so a stale
 *    level never outlives its mute;
 *  - the engine instance's lifetime owns teardown (the former
 *    stop()-clears-the-chip rule: a released engine's controller — memory
 *    included — is gone, so a stale level can never land on a LATER player).
 *
 * Internal: consumed by [LiveMuteController] and this module's jvmTest —
 * not a stable API surface.
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
