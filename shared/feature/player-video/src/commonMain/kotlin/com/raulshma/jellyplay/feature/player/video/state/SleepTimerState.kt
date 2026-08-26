package com.raulshma.jellyplay.feature.player.video.state

import androidx.compose.runtime.Immutable

/**
 * Sleep-timer settings. The remaining-time countdown lives on the ViewModel's
 * `sleepTimerRemainingMs` StateFlow (high-frequency).
 */
@Immutable
data class SleepTimerState(
    val sleepTimerActive: Boolean = false,
    val sleepTimerEndOfEpisode: Boolean = false,
    val sleepTimerLastUsedDurationMs: Long = 0L,
)
