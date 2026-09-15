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
 * ([osLegClaimants]): slice 1 that is READ_ALOUD only — music's OS focus
 * stays on ExoPlayer's built-in handling until the migration slice, so
 * `acquire(MUSIC)` publishes state without touching the arbiter. Seat
 * requests carry the claimant's [PlaybackFocusMatrix.attributesOf] audio
 * attributes (slice-2 checklist: the port takes them per claimant, so the
 * migration slice changes no plumbing when music moves onto this seat).
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
            FocusEvent.LostTransient ->
                _claimState.value = FocusClaimState.Suspended(holder, FocusLossReason.Transient)
            FocusEvent.LostPermanent ->
                _claimState.value = FocusClaimState.Suspended(holder, FocusLossReason.Permanent)
        }
    }

    private companion object {
        /**
         * Claims whose OS audio-focus seat THIS module owns. Slice 1: the
         * read-aloud request only; music still rides ExoPlayer's built-in
         * focus (the migration slice flips `handleAudioFocus` off and adds
         * MUSIC here — a one-line change, which is the point).
         */
        val osLegClaimants: Set<PlaybackSurfaceId> = setOf(PlaybackSurfaceId.READ_ALOUD)
    }
}
