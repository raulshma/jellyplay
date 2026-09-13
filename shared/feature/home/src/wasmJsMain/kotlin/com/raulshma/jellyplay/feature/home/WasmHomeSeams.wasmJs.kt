package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.wallNowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The wasmJs actuals of the home web seams: an honest web platform.
 *
 *  - [WasmHomeClock]: the kotlinx wall clock — `todayIn(currentSystemDefault())`
 *    is the same "current calendar day in the device's system zone" the JVM
 *    TimeSource produced, read from the browser clock (see the seam KDoc for
 *    the locale split).
 *  - [WasmHomeDownloadActions] / [WasmSeriesEpisodeDownloads]: the browser
 *    has no local download pipeline — isSupported = false gates the download
 *    CTAs, the downloaded-ids set stays empty and removal is inert; episode
 *    id reads return an honest empty set (nothing was ever downloaded here).
 *  - [WasmHomeSyncStatusFactory]: the browser has no offline playback outbox,
 *    so the holder is genuinely idle — count 0, empty entries/details, inert
 *    syncNow, trivially-true drain gate. Never a fabricated pending set.
 *  - [WasmHomeNewsletterGate]: the web shell has no newsletter notification
 *    pipeline — the banner never shows (genuinely false, not a fabricated
 *    due-state).
 */
internal object WasmHomeClock : HomeClock {
    override fun nowEpochMillis(): Long = wallNowMillis()
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}

internal object WasmHomeDownloadActions : HomeDownloadActions {
    override val isSupported: Boolean = false
    override val downloadedIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override fun removeDownload(item: MediaItem) {
        // Inert: nothing to remove (see the seam KDoc).
    }
}

internal object WasmSeriesEpisodeDownloads : SeriesEpisodeDownloads {
    override suspend fun downloadedEpisodeIds(seriesId: String): Set<String> = emptySet()
}

internal class WasmHomeSyncStatusFactory : HomeSyncStatusFactory {
    override fun create(scope: CoroutineScope, offlineModeManager: OfflineModeManager): HomeSyncStatus =
        WasmHomeSyncStatus
}

internal object WasmHomeSyncStatus : HomeSyncStatus {
    override val pendingSyncCount: StateFlow<Int> = MutableStateFlow(0)
    override val pendingSyncEntries: StateFlow<List<PlaybackOutboxEntry>> = MutableStateFlow(emptyList())
    private val _pendingItemDetails = MutableStateFlow<Map<String, ResolvedMediaRef>>(emptyMap())
    override val pendingItemDetails: StateFlow<Map<String, ResolvedMediaRef>> = _pendingItemDetails.asStateFlow()
    override fun ensurePendingItemDetails(itemIds: Collection<String>) {
        // Nothing is ever queued on web; the map stays empty.
    }
    override fun syncNow() {
        // Inert: no outbox exists to drain (see the seam KDoc).
    }
    override suspend fun awaitOutboxDrained(): Boolean = true
}

internal object WasmHomeNewsletterGate : HomeNewsletterGate {
    override fun shouldShowBanner(): Flow<Boolean> = flowOf(false)
}
