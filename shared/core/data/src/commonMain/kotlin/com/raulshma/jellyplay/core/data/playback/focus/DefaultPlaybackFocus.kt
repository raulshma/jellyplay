package com.raulshma.jellyplay.core.data.playback.focus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The write-side input for the VIDEO claim's pref-derived policy (the
 * ADR-0004 consequences: user prefs stay VIDEO-side inputs; they ride into
 * the executor through this narrow seam, never through the
 * [PlaybackFocus] interface). Implemented by [DefaultPlaybackFocus]; the
 * VOD wiring pushes on every aggregate emission and the live player asserts
 * its own policy at start — the receiver is one process-singleton policy
 * holder (latest write wins, setter idempotent), so each player's policy is
 * live exactly while that player is.
 *
 * The pref collapse (the legacy dual-mechanism world ran TWO seats —
 * ExoPlayer-builtin gated on `pauseOnAudioFocusLoss`, the manual
 * duck/restore lifecycle gated on `duckOnTransientFocusLoss`): ONE
 * module-owned seat now, gated on `osLegEnabled`. The VOD wiring computes
 * that gate as the prefs' OR — the legacy duck seat ran on the duck pref
 * ALONE (pause-off/duck-on still ducked through phone calls), so pausing
 * off must not drop the seat. When both prefs are on, the duck pref wins
 * the loss directive (it is the more specific intent, and the legacy
 * second-seat steal resolved to the same effective behavior).
 */
interface VideoFocusPolicyInput {

    /**
     * @param osLegEnabled whether VIDEO claims request the OS seat at all.
     *   `false` means the video claim publishes state only — victims still
     *   pause, no OS leg runs, no loss event can arrive (the legacy
     *   pause-off/duck-off posture).
     * @param duckOnTransientLoss whether a transient loss DUCKS a held video
     *   claim (claim stays Held; a regain restores) instead of the default
     *   pause-and-suspend.
     */
    fun onVideoFocusPolicy(osLegEnabled: Boolean, duckOnTransientLoss: Boolean)
}

/**
 * The commonMain executor behind [PlaybackFocus]: the phase machine
 * (Idle / Held / Suspended), synchronous directive dispatch, and the OS
 * bookkeeping through the [FocusArbiter] port. Main-thread confined — the
 * Android adapter marshals arbiter events there; surfaces' commands are
 * main-confined by their own contract.
 *
 * The OS seat is taken only for claims whose OS leg belongs to this module
 * ([osLegClaimants]): READ_ALOUD and MUSIC since their slices; VIDEO since
 * the video slice, runtime-gated by the injected [VideoFocusPolicyInput]
 * (the seat request carries the [PlaybackFocusMatrix.attributesOf] row —
 * the per-claimant-attributes port landed first precisely so this slice
 * changes no plumbing).
 */
class DefaultPlaybackFocus(
    private val arbiter: FocusArbiter,
    private val surfaces: List<PlaybackSurface>,
    private val osLegClaimants: Set<PlaybackSurfaceId> = PlaybackFocusMatrix.DEFAULT_OS_LEG_CLAIMANTS,
) : PlaybackFocus, FocusListener, VideoFocusPolicyInput {

    private val _claimState = MutableStateFlow<FocusClaimState>(FocusClaimState.Idle)
    override val claimState: StateFlow<FocusClaimState> = _claimState.asStateFlow()

    private val commandable = surfaces.associateBy { it.id }

    /** The injected video policy (pref-neutral until the wiring pushes). */
    @Volatile private var videoOsLegEnabled: Boolean = true
    @Volatile private var videoLossDirective: FocusLossDirective? = null

    override fun onVideoFocusPolicy(osLegEnabled: Boolean, duckOnTransientLoss: Boolean) {
        videoOsLegEnabled = osLegEnabled
        videoLossDirective = if (duckOnTransientLoss) FocusLossDirective.Duck(DUCK_VOLUME) else null
    }

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
        if (current is FocusClaimState.Held && inOsLeg(current.holder)) {
            runCatching { arbiter.abandon() }
        }
        if (inOsLeg(claimant)) {
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
        if (inOsLeg(claimant)) runCatching { arbiter.abandon() }
        _claimState.value = FocusClaimState.Idle
    }

    override fun onFocusEvent(event: FocusEvent) {
        val current = _claimState.value
        val holder = (current as? FocusClaimState.Held)?.holder ?: return
        if (!inOsLeg(holder)) return
        when (event) {
            FocusEvent.Regained -> onRegain(holder)
            FocusEvent.LostTransient -> onTransientLoss(holder)
            FocusEvent.LostPermanent -> suspendHolder(holder, FocusLossReason.Permanent)
        }
    }

    /**
     * The per-holder transient-loss dispatch: a [FocusLossDirective.Duck]
     * row (the injected video pref) keeps the claim HELD and ducks — the
     * legacy video semantics; anything else takes the slice-1 suspend +
     * command-pause path.
     */
    private fun onTransientLoss(holder: PlaybackSurfaceId) {
        when (val directive = effectiveLossDirective(holder)) {
            is FocusLossDirective.Duck -> commandable[holder]?.duck(directive.volume)
            FocusLossDirective.Pause -> suspendHolder(holder, FocusLossReason.Transient)
        }
    }

    /**
     * A regain restores a ducked row (the legacy round-trip: pre-duck volume
     * back, mute re-asserted when muted — the surface owns that mechanics)
     * and commands NOTHING on pause rows: resume is manual, pinned.
     */
    private fun onRegain(holder: PlaybackSurfaceId) {
        if (effectiveLossDirective(holder) is FocusLossDirective.Duck) {
            commandable[holder]?.restore()
        }
    }

    /**
     * The suspension-enforcement leg (migration slice). An OS loss on the
     * holder must actually stop the audio: displaced claimants pause
     * themselves by observing [claimState], but the HOLDER has no such
     * observer — the engine would keep producing audio unfocused. So the
     * holder's commandable surface is paused here, synchronously after the
     * state publish (the same command path a victim pause rides; for
     * READ_ALOUD there is deliberately no surface — its reader observes
     * [FocusClaimState.Suspended] and pauses its own loop). The command is
     * the manual-resume guarantee made real: the surface's `pause()` drops
     * playWhenReady, and since [FocusEvent.Regained] is ignored on pause rows
     * and the seat dies with the release edge, nothing resurrects the surface
     * — the user's resume re-acquires ([acquire] from Suspended or the Idle
     * the release edge lands on).
     */
    private fun suspendHolder(holder: PlaybackSurfaceId, reason: FocusLossReason) {
        _claimState.value = FocusClaimState.Suspended(holder, reason)
        commandable[holder]?.pause()
    }

    /** OS-leg membership with the runtime video gate folded in. */
    private fun inOsLeg(claimant: PlaybackSurfaceId): Boolean =
        claimant in osLegClaimants && (claimant != PlaybackSurfaceId.VIDEO || videoOsLegEnabled)

    /** The matrix ruling, overridden on the VIDEO row by the injected pref. */
    private fun effectiveLossDirective(holder: PlaybackSurfaceId): FocusLossDirective =
        if (holder == PlaybackSurfaceId.VIDEO) {
            videoLossDirective ?: PlaybackFocusMatrix.lossDirectiveOf(holder)
        } else {
            PlaybackFocusMatrix.lossDirectiveOf(holder)
        }

    private companion object {
        /**
         * Volume applied on a transient focus loss (duck). Chosen to remain
         * audible-but-quiet during a phone call; the legacy
         * `PlayerAudioLifecycle` literal, moved with the behavior.
         */
        const val DUCK_VOLUME = 0.2f
    }
}
