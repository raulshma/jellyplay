package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.util.TimeSource
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
 * Moved from the legacy `:core:data` shim (playback-flips wave): the sole
 * Android coupling (`android.os.SystemClock.elapsedRealtime`) now goes through
 * the injected [TimeSource] seam — on Android [SystemTimeSource][com.raulshma.jellyplay.core.data.util.SystemTimeSource]
 * delegates to `SystemClock.elapsedRealtime` (the exact legacy source, via the
 * model platform seam), so the countdown behaves identically. Koin-owned
 * ([dataJvmModule][com.raulshma.jellyplay.core.data.di.dataJvmModule]
 * constructs it; the legacy DataModule bridges Hilt injectors to the single).
 */
class SleepTimerManager(
    private val timeSource: TimeSource,
) : AudioSleepTimerManager {

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    override val isSleepTimerActive: StateFlow<Boolean> get() = isActive

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()
    override val sleepTimerRemainingMs: StateFlow<Long> get() = remainingMs

    private companion object {
        const val SLEEP_TIMER_TICK_MS = 5_000L
        const val FADE_OUT_TICK_MS = 100L
        const val DEFAULT_FADE_OUT_DURATION_MS = 10_000L
    }

    private val _isEndOfEpisodeMode = MutableStateFlow(false)
    override val isEndOfEpisodeMode: StateFlow<Boolean> = _isEndOfEpisodeMode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var timerJob: Job? = null

    private var onTimerExpired: (() -> Unit)? = null
    private var onFadeProgress: ((Float) -> Unit)? = null

    override fun setOnTimerExpired(callback: (() -> Unit)?) {
        onTimerExpired = callback
    }

    fun setOnFadeProgress(callback: ((Float) -> Unit)?) {
        onFadeProgress = callback
    }

    fun start(durationMs: Long, fadeOutDurationMs: Long = DEFAULT_FADE_OUT_DURATION_MS) {
        cancel()
        _isActive.value = true
        _isEndOfEpisodeMode.value = false
        _remainingMs.value = durationMs

        val targetElapsedMs = timeSource.nowElapsedRealtimeMillis() + durationMs
        val fadeOutStartMs = fadeOutDurationMs.coerceAtMost(durationMs / 2)

        timerJob = scope.launch {
            var inFadeOutPhase = false

            while (isActive && timeSource.nowElapsedRealtimeMillis() < targetElapsedMs) {
                val remaining = (targetElapsedMs - timeSource.nowElapsedRealtimeMillis()).coerceAtLeast(0)
                _remainingMs.value = remaining

                if (!inFadeOutPhase && remaining <= fadeOutStartMs && fadeOutStartMs > 0) {
                    inFadeOutPhase = true
                }

                if (inFadeOutPhase && remaining > 0) {
                    val progress = (remaining.toFloat() / fadeOutStartMs).coerceIn(0f, 1f)
                    try {
                        onFadeProgress?.invoke(progress)
                    } catch (_: Exception) {}
                    delay(FADE_OUT_TICK_MS)
                } else {
                    delay(SLEEP_TIMER_TICK_MS)
                }
            }
            if (isActive) {
                _isActive.value = false
                _remainingMs.value = 0
                try {
                    onFadeProgress?.invoke(0f)
                    onTimerExpired?.invoke()
                } catch (_: Exception) {}
            }
        }
    }

    override fun startSleepTimer(durationMs: Long) = start(durationMs)

    fun startEndOfEpisode() {
        cancel()
        _isActive.value = true
        _isEndOfEpisodeMode.value = true
        _remainingMs.value = 0
    }

    override fun startEndOfEpisodeTimer() = startEndOfEpisode()

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _isActive.value = false
        _remainingMs.value = 0
        _isEndOfEpisodeMode.value = false
        try {
            onFadeProgress?.invoke(1f)
        } catch (_: Exception) {}
    }

    override fun cancelSleepTimer() = cancel()

    override fun triggerEndOfEpisode() {
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

    override fun getSleepTimerDisplayText(): String = getDisplayText()

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
