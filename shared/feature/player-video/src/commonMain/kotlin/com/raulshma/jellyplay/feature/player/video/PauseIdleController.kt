package com.raulshma.jellyplay.feature.player.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the paused-idle screensaver's arm/disarm state machine (#11): when
 * playback stays paused continuously for the configured budget
 * (`pauseScreensaverAfterMinutes`), [isActive] flips true and the screen
 * overlays the player content with the configured [PauseScreensaverMode]
 * surface (`components/PauseIdleOverlay.kt` — this controller is Compose-free
 * and deliberately does not know the mode; the screen feeds it through the
 * arming gate).
 *
 * Semantics (pinned by [PauseIdleControllerTest]):
 *  - **Arm** — a play→pause edge arms the clock at [nowMs]; arming requires
 *    [isArmingEnabled] (mode != OFF, and the screen's quiet-gate: no open
 *    sheet, no screen lock, no PiP). A pause edge while armed RE-ARMS (the
 *    clock restarts).
 *  - **Fire** — a [tick] at or past the budget flips [isActive] (the 1 Hz
 *    tick loop is the screen's; the controller folds the injected clock so
 *    tests drive time explicitly).
 *  - **Dismiss + reset** — play resumption ([onPlaybackStateChanged](true))
 *    and any user interaction ([onUserInteraction]) dismiss instantly AND
 *    reset the clock: while still paused an interaction re-arms for another
 *    full budget, exactly like a fresh pause.
 *  - **Gate churn** — a tick with [isArmingEnabled] false disarms fully
 *    (an open sheet must not sit under a screensaver); the next arm-eligible
 *    pause edge re-arms.
 *
 * The clock is injected ([nowMs]) and the budget is read live through
 * [idleTimeoutMs] (a settings change mid-pause applies without a rebuild).
 * Zero uiState references — the screen's reader-lambda rule feeds every
 * input. Pinned by [PauseIdleControllerTest].
 */
internal class PauseIdleController(
    /** Live arming gate (mode != OFF ∧ no sheet ∧ not locked ∧ not PiP). */
    private val isArmingEnabled: () -> Boolean,
    /** The idle budget in ms, read live (prefs 1–60 min). */
    private val idleTimeoutMs: () -> Long,
    /** Injected wall clock (ms). */
    private val nowMs: () -> Long,
) {

    private val _isActive = MutableStateFlow(false)

    /** True while the screensaver overlay should cover the player content. */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /** The arm timestamp; `null` = disarmed (playing, gated off, or torn down). */
    private var armedAtMs: Long? = null

    /** Engine/play-state edge: resumption disarms, a pause arms (re-arming restarts the clock). */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        if (isPlaying) {
            disarm()
        } else {
            armIfEligible()
        }
    }

    /**
     * Any user interaction (tap/key/D-pad): dismiss the active overlay
     * instantly and reset the idle clock — while still paused the controller
     * re-arms for another full budget. A no-op while playing (nothing armed).
     */
    fun onUserInteraction() {
        if (_isActive.value || armedAtMs != null) {
            _isActive.value = false
            armIfEligible()
        }
    }

    /**
     * The periodic fold (the screen's 1 Hz loop while paused): fires the
     * overlay once the budget elapses; a gate that went dark (sheet/lock/PiP)
     * disarms fully.
     */
    fun tick() {
        if (!isArmingEnabled()) {
            if (_isActive.value || armedAtMs != null) disarm()
            return
        }
        val armedAt = armedAtMs ?: return
        if (_isActive.value) return
        val budget = idleTimeoutMs().coerceAtLeast(0L)
        if (nowMs() - armedAt >= budget) {
            _isActive.value = true
        }
    }

    /** Full disarm (teardown, item switch) — overlay down, clock cleared. */
    fun disarm() {
        armedAtMs = null
        _isActive.value = false
    }

    private fun armIfEligible() {
        _isActive.value = false
        armedAtMs = if (isArmingEnabled()) nowMs() else null
    }
}
