package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether proactive audio-cache prefetching is currently allowed.
 * Passive cache-on-play (ExoPlayer side-caching) is never gated — only
 * ahead-of-playhead warming consults this guard.
 *
 * Combines the user's [AudioCacheNetworkPolicy], the live network status
 * (metered vs unmetered via [NetworkMonitor.isMetered]), and a rolling
 * monthly cellular-bytes counter (in-memory; resets lazily when the calendar
 * month changes).
 */
@Singleton
class AudioCachePolicyGuard @Inject constructor(
    private val audioCacheStore: AudioCacheStore,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val scope: CoroutineScope,
) {
    // In-memory cellular counter. Resets lazily on month rollover.
    private val cellularBytesThisMonth = MutableStateFlow(0L)
    private val cellularMonthEpoch = MutableStateFlow(currentMonthEpoch())

    val isPrefetchAllowed: StateFlow<Boolean> = combine(
        audioCacheStore.audioCache,
        networkMonitor.networkStatus,
        networkMonitor.isMetered,
        cellularBytesThisMonth,
    ) { prefs, network, metered, bytes ->
        // Lazy month rollover
        val currentEpoch = currentMonthEpoch()
        val effectiveBytes = if (currentEpoch != cellularMonthEpoch.value) {
            cellularBytesThisMonth.value = 0L
            cellularMonthEpoch.value = currentEpoch
            0L
        } else {
            bytes
        }
        evaluate(prefs, network, metered, effectiveBytes)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    private fun evaluate(
        prefs: AudioCacheSlice,
        network: NetworkStatus,
        metered: Boolean,
        cellularBytes: Long,
    ): Boolean {
        if (!network.hasNetwork) return false
        return when (prefs.audioCacheNetworkPolicy) {
            AudioCacheNetworkPolicy.OFF -> false
            AudioCacheNetworkPolicy.WIFI_ONLY -> !metered
            AudioCacheNetworkPolicy.ANY_NETWORK -> {
                if (!metered) true
                else {
                    val capBytes = prefs.audioCacheCellularMonthlyCapMb.toLong() * 1024L * 1024L
                    cellularBytes < capBytes
                }
            }
        }
    }

    /** Called by [AudioPrefetchEngine] after a cellular warm completes. */
    fun recordCellularPrefetch(bytes: Long) {
        val currentEpoch = currentMonthEpoch()
        if (currentEpoch != cellularMonthEpoch.value) {
            cellularBytesThisMonth.value = bytes
            cellularMonthEpoch.value = currentEpoch
        } else {
            cellularBytesThisMonth.value += bytes
        }
    }

    /** Called from Settings → "Reset cellular counter". */
    fun resetCellularCounter() {
        cellularBytesThisMonth.value = 0L
        cellularMonthEpoch.value = currentMonthEpoch()
    }

    private fun currentMonthEpoch(): Long =
        LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
