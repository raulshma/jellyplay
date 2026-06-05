package com.raulshma.jellyplay.core.data.playback

import android.os.SystemClock
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor() {

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private companion object {
        const val SLEEP_TIMER_TICK_MS = 5_000L
    }

    private val _isEndOfEpisodeMode = MutableStateFlow(false)
    val isEndOfEpisodeMode: StateFlow<Boolean> = _isEndOfEpisodeMode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var timerJob: Job? = null

    private var onTimerExpired: (() -> Unit)? = null

    fun setOnTimerExpired(callback: (() -> Unit)?) {
        onTimerExpired = callback
    }

    fun start(durationMs: Long) {
        cancel()
        _isActive.value = true
        _isEndOfEpisodeMode.value = false
        _remainingMs.value = durationMs

        val targetElapsedMs = SystemClock.elapsedRealtime() + durationMs

        timerJob = scope.launch {
            while (isActive && SystemClock.elapsedRealtime() < targetElapsedMs) {
                delay(SLEEP_TIMER_TICK_MS)
                _remainingMs.value = (targetElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            }
            if (isActive) {
                _isActive.value = false
                _remainingMs.value = 0
                try {
                    onTimerExpired?.invoke()
                } catch (_: Exception) {}
            }
        }
    }

    fun startEndOfEpisode() {
        cancel()
        _isActive.value = true
        _isEndOfEpisodeMode.value = true
        _remainingMs.value = 0
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _isActive.value = false
        _remainingMs.value = 0
        _isEndOfEpisodeMode.value = false
    }

    fun triggerEndOfEpisode() {
        if (_isEndOfEpisodeMode.value && _isActive.value) {
            cancel()
            try {
                onTimerExpired?.invoke()
            } catch (_: Exception) {}
        }
    }

    fun getDisplayText(): String {
        if (!_isActive.value) return ""
        if (_isEndOfEpisodeMode.value) return "End of episode"
        val remaining = _remainingMs.value
        return formatRemainingTime(remaining)
    }

    private fun formatRemainingTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
