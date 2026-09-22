package com.raulshma.jellyplay.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The idle decision the monitor ticks on — pure so the ladder is
 * unit-pinned (`DesktopIdleMonitorTest`):
 *
 *  1. the feature must be enabled AND the timeout non-zero (0 = "Off" in
 *     the picker);
 *  2. nothing may be playing (audio queue empty AND no bound video engine);
 *  3. the window must be active (focused/visible) — an occluded or
 *     minimized window never idles into the ambient screen;
 *  4. the debounce must have elapsed since the last user input.
 */
internal fun shouldEnterIdle(
    enabled: Boolean,
    timeoutMin: Long,
    lastInputMs: Long,
    nowMs: Long,
    audioPlaying: Boolean,
    videoActive: Boolean,
    windowActive: Boolean,
): Boolean {
    if (!enabled || timeoutMin <= 0L) return false
    if (audioPlaying || videoActive) return false
    if (!windowActive) return false
    return nowMs - lastInputMs >= timeoutMin * 60_000L
}

/** The idle-ambient settings snapshot the monitor reads per tick. */
internal data class IdleAmbientSettings(
    val enabled: Boolean,
    val timeoutMin: Long,
)

/**
 * The desktop idle "Ready to play" ambient screen's state machine.
 * A 1 s tick loop re-evaluates [shouldEnterIdle] over injected probes (the
 * audio queue, the active-engine registry, the AWT window state, the
 * screensaver-store settings); any user input resets the debounce AND
 * dismisses an already-shown overlay immediately; playback start clears it
 * on the next tick (≤ 1 s).
 *
 * All probes are `() -> …` lambdas so the monitor is JVM-testable without
 * AWT, engines or DataStore (fake probes + a virtual clock).
 */
internal class DesktopIdleMonitor(
    private val settings: () -> IdleAmbientSettings,
    private val isAudioPlaying: () -> Boolean,
    private val isVideoActive: () -> Boolean,
    private val isWindowActive: () -> Boolean,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private val _isIdle = MutableStateFlow(false)
    val isIdle: StateFlow<Boolean> = _isIdle.asStateFlow()

    @Volatile
    private var lastInputMs: Long = nowMs()

    private var tickJob: Job? = null

    /** Any key/pointer input: reset the debounce and dismiss immediately. */
    fun onUserInput() {
        lastInputMs = nowMs()
        _isIdle.value = false
    }

    /** Starts the 1 s tick loop on [scope]; idempotent. */
    fun start(scope: CoroutineScope) {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _isIdle.value = false
    }

    /** One evaluation — internal so the test can step the machine manually. */
    internal fun tick() {
        val s = settings()
        _isIdle.value = shouldEnterIdle(
            enabled = s.enabled,
            timeoutMin = s.timeoutMin,
            lastInputMs = lastInputMs,
            nowMs = nowMs(),
            audioPlaying = isAudioPlaying(),
            videoActive = isVideoActive(),
            windowActive = isWindowActive(),
        )
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
