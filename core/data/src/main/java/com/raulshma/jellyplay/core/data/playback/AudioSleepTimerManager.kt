package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.flow.StateFlow

interface AudioSleepTimerManager {
    val isSleepTimerActive: StateFlow<Boolean>
    val sleepTimerRemainingMs: StateFlow<Long>
    val isEndOfEpisodeMode: StateFlow<Boolean>

    fun startSleepTimer(durationMs: Long)
    fun startEndOfEpisodeTimer()
    fun cancelSleepTimer()
    fun triggerEndOfEpisode()
    fun setOnTimerExpired(callback: (() -> Unit)?)
    fun getSleepTimerDisplayText(): String
}
