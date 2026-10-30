package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/**
 * The session-owned engine-command funnel (the [TransportRouter] sibling,
 * constructed in [PlaybackSession] beside it): the DIRECT engine commands
 * that never ride the SyncPlay → cast → local routing ladder because they
 * address the decode surface itself — the playback-speed family (with the
 * hold-speed gesture's restore state) and the engine's polling/scrubbing
 * cadence. The former [VideoPlayerViewModel] handlers moved here with their
 * constants and their mutable state; the VM's event arms forward one-line.
 *
 * The speed family:
 *  - [setPlaybackSpeed] — a user-initiated speed change resets the pass-out
 *    interaction clock (the still-watching counter feeds through the same
 *    signal) before the engine command, exactly like every other user
 *    transport action;
 *  - [startHoldSpeed]/[stopHoldSpeed] — the long-press speed boost. The
 *    captured pre-hold speed is THIS module's mutable state (`speedBeforeHold`),
 *    so the restore-on-release rule has one home; a null capture (no
 *    [setPlaybackSpeed] before the press) restores the configured
 *    [defaultSpeed] instead.
 *
 * The cadence family: [setControlsVisible] carries the controls-visibility
 * polling constants (a tighter 4 Hz cadence while controls are on screen, a
 * relaxed 1 Hz heartbeat behind them) and [setScrubbing] brackets a
 * scrubbing session (Media3's decode-only-frames mode; other engines no-op).
 *
 * The per-arm effects arrive as constructor lambdas (the
 * [PipTransportController] seam shape) so this class never touches the
 * session's collaborators or the uiState bag directly.
 */
internal class EngineCommands(
    /** Re-read per use, so engine swaps between calls are observed. */
    private val getEngine: () -> MediaEngine?,
    /** The pass-out interaction clock reset + still-watching counter feed. */
    private val onUserInteraction: () -> Unit,

    // ── The speed-mirror seams (the TransportRouter's narrow-lambda shape) ──
    private val currentSpeed: () -> Float,
    private val holdSpeedArmed: () -> Boolean,
    private val holdSpeedMultiplier: () -> Float,
    private val defaultSpeed: () -> Float,
    /** Writes the uiState `playbackSpeed` mirror. */
    private val writeSpeed: (Float) -> Unit,
    /** Writes the speed + hold-arm mirror pair (one uiState write, not two). */
    private val writeHoldSpeed: (speed: Float, armed: Boolean) -> Unit,
) {

    /**
     * The speed the hold gesture will restore on release, captured at press
     * time; `null` while no hold is live.
     */
    private var speedBeforeHold: Float? = null

    /** The speed picker's arm (and every other explicit speed change). */
    fun setPlaybackSpeed(speed: Float) {
        // A user-initiated speed change resets the pass-out interaction clock
        // and feeds the still-watching counter through the same signal.
        onUserInteraction()
        writeSpeed(speed)
        getEngine()?.setPlaybackSpeed(speed)
    }

    /** The long-press speed boost's press arm. */
    fun startHoldSpeed() {
        if (holdSpeedArmed()) return
        speedBeforeHold = currentSpeed()
        val targetSpeed = holdSpeedMultiplier()
        getEngine()?.setPlaybackSpeed(targetSpeed)
        writeHoldSpeed(targetSpeed, true)
    }

    /** The long-press speed boost's release arm. */
    fun stopHoldSpeed() {
        if (!holdSpeedArmed()) return
        val restoreSpeed = speedBeforeHold ?: defaultSpeed()
        speedBeforeHold = null
        getEngine()?.setPlaybackSpeed(restoreSpeed)
        writeHoldSpeed(restoreSpeed, false)
    }

    /**
     * Scrubbing-session bracket for the engine (seek-bar drag, swipe seek,
     * double-tap-hold). Media3's adapter maps this to
     * `setScrubbingModeEnabled` — decode-only frames while the drag is live —
     * other engines no-op. See [MediaEngine.onScrubbingChanged].
     */
    fun setScrubbing(active: Boolean) {
        getEngine()?.onScrubbingChanged(active)
    }

    /**
     * The controls-visibility cadence: a tighter polling interval while the
     * controls are visible (the seek bar/stats read up to 4 Hz), the relaxed
     * heartbeat behind them. Direct engine command — the cadence is a decode
     * concern, not a transport-routed one.
     */
    fun setControlsVisible(visible: Boolean) {
        getEngine()?.setPollingIntervalMs(if (visible) CONTROLS_VISIBLE_POLLING_MS else CONTROLS_HIDDEN_POLLING_MS)
    }

    private companion object {
        /** The controls-visible polling cadence (the engine's position-tick rate). */
        const val CONTROLS_VISIBLE_POLLING_MS = 250L

        /** The background heartbeat once the controls hide. */
        const val CONTROLS_HIDDEN_POLLING_MS = 1_000L
    }
}

/**
 * The composition wiring for [PlaybackSession.engineCommands] — moved out of
 * the session body (the composition root's line guard) into the module being
 * wired, the same ownership move the [transportRouter] wiring made. Reads the
 * session's internal collaborators through the receiver (lazily — the lambdas
 * defer every read of the later-declared coordinator, the usual
 * construction-order rule) and the owner-private handles through the
 * parameter; the uiState never crosses by name (the narrow-lambda discipline —
 * the bag stays inference-only here too).
 */
internal fun PlaybackSession.engineCommands(handles: PlayerStateHandles): EngineCommands {
    val uiState = handles.uiState
    return EngineCommands(
        getEngine = { playerSessionManager.engine },
        onUserInteraction = { engineEventCoordinator.onUserInteraction() },
        currentSpeed = { uiState.value.playbackSpeed },
        holdSpeedArmed = { uiState.value.gestures.isHoldSpeedActive },
        holdSpeedMultiplier = { uiState.value.gestures.holdSpeedMultiplier },
        defaultSpeed = { uiState.value.gestures.defaultSpeed },
        writeSpeed = { speed -> uiState.update { it.copy(playbackSpeed = speed) } },
        writeHoldSpeed = { speed, armed ->
            uiState.update {
                it.copy(playbackSpeed = speed, gestures = it.gestures.copy(isHoldSpeedActive = armed))
            }
        },
    )
}
