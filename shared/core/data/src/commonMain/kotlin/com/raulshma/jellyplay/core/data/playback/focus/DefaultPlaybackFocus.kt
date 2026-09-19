package com.raulshma.jellyplay.core.data.playback.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The commonMain executor behind [PlaybackFocus]: the phase machine
 * (Idle / Held / Suspended), synchronous directive dispatch, and the OS
 * bookkeeping through the [FocusArbiter] port. Main-thread confined — the
 * Android adapter marshals arbiter events there; surfaces' `pause()` is
 * main-confined by its own contract.
 *
 * The OS seat is taken only for claims whose OS leg belongs to this module
 * ([osLegClaimants]): since the migration slice that is READ_ALOUD and MUSIC
 * — music's seat request carries the [PlaybackFocusMatrix.attributesOf]
 * MUSIC row (the per-claimant-attributes port landed first precisely so this
 * slice changes no plumbing). VIDEO still sits outside (its OS focus lives in
 * PlayerAudioLifecycle; `acquire(VIDEO)` is denied by the closed world).
 */
class DefaultPlaybackFocus(
    private val arbiter: FocusArbiter,
    private val surfaces: List<PlaybackSurface>,
) : PlaybackFocus, FocusListener {

    private val _claimState = MutableStateFlow<FocusClaimState>(FocusClaimState.Idle)
    override val claimState: StateFlow<FocusClaimState> = _claimState.asStateFlow()

    private val commandable = surfaces.associateBy { it.id }

    override fun acquire(claimant: PlaybackSurfaceId): FocusOutcome {
        if (!PlaybackFocusMatrix.isGrantable(claimant)) return FocusOutcome.Denied
        val current = _claimState.value
        when (current) {
            is FocusClaimState.Held -> if (current.holder == claimant) return FocusOutcome.Granted
            is FocusClaimState.Suspended -> if (current.holder != claimant) return FocusOutcome.Denied
            FocusClaimState.Idle -> Unit
        }
        // A different holder is fine — newest-wins: the incoming claim evicts.
        // A displaced holder's OS seat dies with its claim: abandon it here,
        // not only in [release] — the reader pauses a displaced read-aloud
        // WITHOUT releasing, so its AudioFocusRequest would otherwise stay
        // outstanding until session end (one seat request at a time, and a
        // re-acquire re-requests the seat anyway).
        if (current is FocusClaimState.Held && current.holder in osLegClaimants) {
            runCatching { arbiter.abandon() }
        }
        if (claimant in osLegClaimants) {
            val granted = runCatching {
                arbiter.request(PlaybackFocusMatrix.attributesOf(claimant), this)
            }.getOrDefault(false)
            if (!granted) return FocusOutcome.Denied
        }
        PlaybackFocusMatrix.victimsOf(claimant).forEach { id ->
            commandable[id]?.pause()
        }
        _claimState.value = FocusClaimState.Held(claimant)
        return FocusOutcome.Granted
    }

    override fun release(claimant: PlaybackSurfaceId) {
        val current = _claimState.value
        val holder = when (current) {
            is FocusClaimState.Held -> current.holder
            is FocusClaimState.Suspended -> current.holder
            FocusClaimState.Idle -> return
        }
        if (holder != claimant) return
        if (claimant in osLegClaimants) runCatching { arbiter.abandon() }
        _claimState.value = FocusClaimState.Idle
    }

    override fun onFocusEvent(event: FocusEvent) {
        val current = _claimState.value
        val holder = (current as? FocusClaimState.Held)?.holder ?: return
        if (holder !in osLegClaimants) return
        when (event) {
            FocusEvent.Regained -> Unit // resume is manual — a regain never restarts audio
            FocusEvent.LostTransient -> suspendHolder(holder, FocusLossReason.Transient)
            FocusEvent.LostPermanent -> suspendHolder(holder, FocusLossReason.Permanent)
        }
    }

    /**
     * The suspension-enforcement leg (migration slice). An OS loss on the
     * holder must actually stop the audio: displaced claimants pause
     * themselves by observing [claimState], but the HOLDER has no such
     * observer — MUSIC's engine would keep producing audio unfocused. So the
     * holder's commandable surface is paused here, synchronously after the
     * state publish (the same command path a victim pause rides; for
     * READ_ALOUD there is deliberately no surface — its reader observes
     * [FocusClaimState.Suspended] and pauses its own loop). The command is
     * the manual-resume guarantee made real: the surface's `pause()` drops
     * playWhenReady, and since [FocusEvent.Regained] is ignored and the seat
     * dies with the release edge, nothing resurrects the surface — the
     * user's resume re-acquires ([acquire] from Suspended or the Idle the
     * release edge lands on).
     */
    private fun suspendHolder(holder: PlaybackSurfaceId, reason: FocusLossReason) {
        _claimState.value = FocusClaimState.Suspended(holder, reason)
        commandable[holder]?.pause()
    }

    private companion object {
        /**
         * Claims whose OS audio-focus seat THIS module owns. READ_ALOUD since
         * slice 1; MUSIC since the migration slice (ExoPlayer's built-in
         * handling is off — BOTH Android music players, primary and crossfade
         * secondary, ship `setAudioAttributes(..., false)`, or a second
         * request would fight this seat).
         */
        val osLegClaimants: Set<PlaybackSurfaceId> =
            setOf(PlaybackSurfaceId.READ_ALOUD, PlaybackSurfaceId.MUSIC)
    }
}
