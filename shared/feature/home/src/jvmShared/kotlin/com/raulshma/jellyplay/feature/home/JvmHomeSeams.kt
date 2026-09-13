package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.download.MediaDownloadActions
import com.raulshma.jellyplay.core.data.newsletter.NewsletterTriggerManager
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolder
import com.raulshma.jellyplay.core.data.sync.SyncStatusStateHolderFactory
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate

/**
 * The JVM adapters over the core:data jvmShared singles — the home
 * ViewModel/refresher's reads and commands delegate verbatim to
 * `TimeSource` / `MediaDownloadActions` / `DownloadRepository` /
 * `SyncStatusStateHolder(+Factory)` / `NewsletterTriggerManager`
 * (android/desktop behavior unchanged). Each adapter only narrows or
 * re-shapes the JVM surface into its wasm-safe feature seam:
 *  - [JvmHomeClock] keeps the java.time `today(system zone)` wall read the
 *    TimeSource always had (the refresher's former
 *    `timeSource.today(ZoneOffset.systemDefault())` verbatim);
 *  - [JvmHomeSyncStatusFactory] builds the real holder via the real
 *    factory — the sheet/badge/drain flows ARE the holder's own;
 *  - [JvmSeriesEpisodeDownloads] maps the repository's
 *    `getDownloadedEpisodeIdsForSeries` read verbatim.
 */
internal class JvmHomeClock(
    private val timeSource: TimeSource,
) : HomeClock {
    override fun nowEpochMillis(): Long = timeSource.nowEpochMillis()
    override fun today(): LocalDate = timeSource.today(java.time.ZoneId.systemDefault()).toKotlinLocalDate()
}

internal class JvmHomeDownloadActions(
    private val actions: MediaDownloadActions,
) : HomeDownloadActions {
    override val isSupported: Boolean = true
    override val downloadedIds: StateFlow<Set<String>> = actions.downloadedIds
    override fun removeDownload(item: MediaItem) = actions.removeDownload(item)
}

internal class JvmSeriesEpisodeDownloads(
    private val downloadRepository: DownloadRepository,
) : SeriesEpisodeDownloads {
    override suspend fun downloadedEpisodeIds(seriesId: String): Set<String> =
        downloadRepository.getDownloadedEpisodeIdsForSeries(seriesId)
}

internal class JvmHomeSyncStatus(
    private val holder: SyncStatusStateHolder,
) : HomeSyncStatus {
    override val pendingSyncCount: StateFlow<Int> = holder.pendingSyncCount
    override val pendingSyncEntries: StateFlow<List<PlaybackOutboxEntry>> =
        holder.pendingSyncEntries
    override val pendingItemDetails: StateFlow<Map<String, ResolvedMediaRef>> =
        holder.pendingItemDetails
    override fun ensurePendingItemDetails(itemIds: Collection<String>) = holder.ensurePendingItemDetails(itemIds)
    override fun syncNow() = holder.syncNow()
    override suspend fun awaitOutboxDrained(): Boolean = holder.awaitOutboxDrained()
}

internal class JvmHomeSyncStatusFactory(
    private val factory: SyncStatusStateHolderFactory,
) : HomeSyncStatusFactory {
    override fun create(scope: CoroutineScope, offlineModeManager: OfflineModeManager): HomeSyncStatus =
        JvmHomeSyncStatus(factory.create(scope, offlineModeManager))
}

internal class JvmHomeNewsletterGate(
    private val manager: NewsletterTriggerManager,
) : HomeNewsletterGate {
    override fun shouldShowBanner(): Flow<Boolean> = manager.shouldShowBanner()
}
