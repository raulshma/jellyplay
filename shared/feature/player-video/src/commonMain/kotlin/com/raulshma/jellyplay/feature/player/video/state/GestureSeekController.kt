package com.raulshma.jellyplay.feature.player.video.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the gesture-seek / volume / brightness overlay state and the
 * commit-vs-cancel asymmetry, extracted out of `VideoPlayerScreen`.
 *
 * Unlike [TrackSelectionHelper]/[SubtitleManager] (which read/write the VM's
 * `_uiState`), the gesture state is composable-local and touches Android
 * `Window`/`AudioManager` directly — it does not flow through `VideoPlayerUiState`
 * (which has no seekPositionMs/isGestureSeeking fields). So this controller is a
 * plain `class` constructed in the screen via `remember`, holding the gesture
 * state internally and exposing [StateFlow]s for the overlay to observe.
 *
 * Android I/O (`Window`, `AudioManager`) moves behind small injected lambdas —
 * real in prod, fake in tests. The pure math lives in [GestureSeekMath].
 *
 * **Saveability:** the previous screen-local state was `rememberSaveable` to
 * survive config changes. The controller's `StateFlow`s are in-memory, so an
 * in-flight gesture resets on rotation. This is intentional: a half-finished
 * swipe already behaves poorly across rotation, and the simplification is the
 * point of the extraction. (Gestures don't span config changes by design.)
 *
 * **Critical asymmetry** (pinned by `GestureSeekControllerTest`):
 *  - [onClearOverlays] **commits** — seeks, persists brightness. Volume
 *    gestures write the system stream / cast volume live via [onVolumeGesture]
 *    (Android persists STREAM_MUSIC across sessions itself), so no volume
 *    persistence is needed on commit.
 *  - [onCancelOverlays] **discards** — restores the pre-gesture brightness and
 *    seeks nowhere. Multi-touch (pinch-zoom) cancels.
 */
internal class GestureSeekController(
    private val scope: CoroutineScope,
    private val getEngine: () -> com.raulshma.jellyplay.feature.player.video.engine.MediaEngine?,
    private val getSwipeSeekMaxMs: () -> Long,
    private val isCastConnected: () -> Boolean,
    private val getCastVolume: () -> Float,
    // I/O seams (real in prod, fake in test):
    private val readWindowBrightness: () -> Float,
    private val writeWindowBrightness: (Float) -> Unit,
    private val restoreWindowBrightness: (Float) -> Unit,
    private val readStreamVolume: () -> Pair<Int, Int>,          // (current, max)
    private val writeStreamVolume: (Int) -> Unit,
    // Side-effect callbacks into the VM:
    private val doSeekTo: (Long) -> Unit,
    private val saveBrightness: (Float) -> Unit,
    private val setCastVolume: (Float) -> Unit,
    private val dismissDelayMs: Long = GESTURE_BARS_DISMISS_MS,
) {
    private val _brightnessOverlay = MutableStateFlow(-1f)
    val brightnessOverlay: StateFlow<Float> = _brightnessOverlay.asStateFlow()

    private val _volumeOverlay = MutableStateFlow(-1f)
    val volumeOverlay: StateFlow<Float> = _volumeOverlay.asStateFlow()

    private val _seekPositionMs = MutableStateFlow(0L)
    val seekPositionMs: StateFlow<Long> = _seekPositionMs.asStateFlow()

    private val _deltaMs = MutableStateFlow(0L)
    val deltaMs: StateFlow<Long> = _deltaMs.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    // Internal gesture state (not exposed to the overlay — not visual).
    private var startPositionMs: Long = 0L
    private var initialBrightnessOnGestureStart: Float = -1f
    private var volumeGestureAccumulator: Float = 0f
    private var overlayDismissJob: Job? = null

    /** Capture the pre-gesture window brightness so cancel can restore it. */
    fun onStartGesture() {
        initialBrightnessOnGestureStart = readWindowBrightness()
    }

    fun onSeekGesture(totalDeltaMs: Long) {
        val eng = getEngine() ?: return
        if (!_isSeeking.value) {
            startPositionMs = eng.currentPositionMs
            _isSeeking.value = true
        }
        _deltaMs.value = totalDeltaMs
        val durationMs = eng.durationMs.coerceAtLeast(0)
        _seekPositionMs.value = GestureSeekMath.seekTarget(
            startPositionMs = startPositionMs,
            totalDeltaMs = totalDeltaMs,
            durationMs = durationMs,
            swipeSeekMaxMs = getSwipeSeekMaxMs(),
        )
    }

    fun onBrightnessGesture(delta: Float) {
        val current = readWindowBrightness()
        val target = GestureSeekMath.brightnessTarget(current, delta)
        writeWindowBrightness(target)
        _brightnessOverlay.value = target
    }

    fun onVolumeGesture(delta: Float) {
        if (isCastConnected()) {
            // Cast volume: continuous float, applied immediately via setCastVolume.
            val currentNorm = getCastVolume()
            volumeGestureAccumulator += delta
            val newVolume = GestureSeekMath.castVolumeTarget(currentNorm, volumeGestureAccumulator)
            _volumeOverlay.value = newVolume
            setCastVolume(newVolume)
        } else {
            // Local volume: quantize to discrete hardware steps.
            val (current, max) = readStreamVolume()
            if (max <= 0) return
            val currentNorm = current.toFloat() / max.toFloat()
            val stepThreshold = 1f / max.toFloat()
            volumeGestureAccumulator += delta
            _volumeOverlay.value = (currentNorm + volumeGestureAccumulator).coerceIn(0f, 1f)
            val (steps, remainder) = GestureSeekMath.localVolumeStep(
                accumulator = volumeGestureAccumulator,
                stepThreshold = stepThreshold,
                maxSteps = max,
            )
            if (steps != 0) {
                volumeGestureAccumulator = remainder
                val newVol = (current + steps).coerceIn(0, max)
                writeStreamVolume(newVol)
            }
        }
    }

    /**
     * Commit path (normal gesture release). Seeks to the clamped target, persists
     * brightness/volume, then schedules a delayed hide of the visual indicators.
     */
    fun onClearOverlays() {
        if (_isSeeking.value) {
            doSeekTo(_seekPositionMs.value)
        }
        val brightness = _brightnessOverlay.value
        if (brightness in 0f..1f) {
            saveBrightness(brightness)
        }
        resetGestureState()
        // Hide the visual indicators after a short delay (persist already happened
        // synchronously above). Cancels any prior pending dismiss job first.
        overlayDismissJob?.cancel()
        overlayDismissJob = scope.launch {
            delay(dismissDelayMs)
            _brightnessOverlay.value = -1f
            _volumeOverlay.value = -1f
        }
    }

    /**
     * Cancel path (multi-touch / pinch-zoom). Discards the in-flight change:
     * seeks nowhere and restores the pre-gesture window brightness.
     */
    fun onCancelOverlays() {
        // Restore brightness captured at gesture start (or the system default).
        restoreWindowBrightness(initialBrightnessOnGestureStart)
        // Cancel any pending dismiss job from a prior gesture so it doesn't fire
        // redundantly after we've already cleared the overlays here.
        overlayDismissJob?.cancel()
        overlayDismissJob = null
        _brightnessOverlay.value = -1f
        _volumeOverlay.value = -1f
        volumeGestureAccumulator = 0f
        _isSeeking.value = false
        startPositionMs = 0L
    }

    private fun resetGestureState() {
        volumeGestureAccumulator = 0f
        _isSeeking.value = false
        startPositionMs = 0L
    }

    companion object {
        /** Delay before the gesture bars auto-hide after a commit. */
        const val GESTURE_BARS_DISMISS_MS: Long = 800L
    }
}
