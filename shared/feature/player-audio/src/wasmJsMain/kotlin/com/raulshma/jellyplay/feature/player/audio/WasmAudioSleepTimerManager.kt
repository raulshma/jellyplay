package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioSleepTimerManager
import com.raulshma.jellyplay.core.data.util.EpochMillisSource
import com.raulshma.jellyplay.core.model.wallNowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The wasmJs actual of [AudioSleepTimerManager]: a wall-clock countdown — the
 * JVM graph binds the interface to core:data's SleepTimerManager (elapsed-
 * realtime based; desktop's monotonic nanoTime clock), which the browser has
 * no equivalent for. Wall time is the honest degrade for a sleep timer: it
 * only misbehaves if the OS clock jumps mid-countdown, the same weakness a
 * wristwatch countdown has. No fade-out ramp — the audio VM only consumes the
 * expiry callback ([com.raulshma.jellyplay.feature.player.audio.AudioSleepTimerController.armExpiryPause]),
 * the fade lives in the JVM manager's own loop.
 */
internal class WasmAudioSleepTimerManager : AudioSleepTimerManager {

    private val clock: EpochMillisSource = EpochMillisSource { wallNowMillis() }

    private val _isActive = MutableStateFlow(false)
    override val isSleepTimerActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    override val sleepTimerRemainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private val _isEndOfEpisodeMode = MutableStateFlow(false)
    override val isEndOfEpisodeMode: StateFlow<Boolean> = _isEndOfEpisodeMode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var timerJob: Job? = null
    private var onTimerExpired: (() -> Unit)? = null

    override fun setOnTimerExpired(callback: (() -> Unit)?) {
        onTimerExpired = callback
    }

    override fun startSleepTimer(durationMs: Long) {
        cancelSleepTimer()
        _isActive.value = true
        _isEndOfEpisodeMode.value = false
        _remainingMs.value = durationMs

        val targetMs = clock.nowEpochMillis() + durationMs
        timerJob = scope.launch {
            while (isActive && clock.nowEpochMillis() < targetMs) {
                _remainingMs.value = (targetMs - clock.nowEpochMillis()).coerceAtLeast(0)
                // 5 s tick, matching the JVM manager's pre-fade cadence — the
                // countdown text renders at second granularity anyway.
                delay(5_000L)
            }
            if (isActive) {
                _isActive.value = false
                _remainingMs.value = 0
                // Swallow callback failures like the JVM manager — a throwing
                // engine.pause() must not cancel this job into the uncaught
                // handler.
                runCatching { onTimerExpired?.invoke() }
            }
        }
    }

    override fun startEndOfEpisodeTimer() {
        cancelSleepTimer()
        _isActive.value = true
        _isEndOfEpisodeMode.value = true
        _remainingMs.value = 0
    }

    override fun cancelSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        _isActive.value = false
        _remainingMs.value = 0
        _isEndOfEpisodeMode.value = false
    }

    override fun triggerEndOfEpisode() {
        if (_isEndOfEpisodeMode.value && _isActive.value) {
            cancelSleepTimer()
            runCatching { onTimerExpired?.invoke() }
        }
    }

    override fun getSleepTimerDisplayText(): String {
        if (!_isActive.value) return ""
        if (_isEndOfEpisodeMode.value) return "End of episode"
        val totalSeconds = _remainingMs.value / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        else "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
