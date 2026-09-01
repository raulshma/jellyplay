package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Minimal message sink so core/data can report download outcomes without
 * depending on core/ui's UserMessageBus.
 */
interface DownloadOutcomeMessenger {
    fun downloadStarted()
    fun downloadStartFailed()
}

/**
 * The download/remove half of a long-press quick-action menu, shared by every
 * host surface (library, favorites, search, studio, detail rows — issue #147:
 * "download/delete options on the press-and-hold menu" everywhere).
 *
 * Owns exactly the two stateless halves the screens kept re-wiring per
 * ViewModel: the downloaded-id set that flips a card's DOWNLOAD slot to
 * REMOVE_DOWNLOAD ([downloadedIds] — completed ids ∪ series ids, the union
 * contract on [DownloadRepository.observeDownloadedIdsIncludingSeries]), and
 * the delete routing ([removeDownload] — series cards delete the whole series
 * download, anything else the single item; never touches the server).
 *
 * [download] is suspend and outcome-returning on purpose: hosts with richer
 * routing (`LibraryViewModel` pre-opens the series selection sheet,
 * `DetailViewModel` owns a local message queue) branch on it themselves.
 * Every plain host uses [downloadAndReport], which folds the shared cascade
 * in here: Started/Failed surface through the injected
 * [DownloadOutcomeMessenger] (UserMessageBus lives in core/ui, which
 * core/data must not depend on) and both navigation outcomes route to the
 * host's open-detail callback.
 *
 * Koin-owned construction (jvmShared convention): no @Inject/@Singleton —
 * DataKoinModule wires the process scope and dependencies, mirroring the
 * other moved repositories.
 */
class MediaDownloadActions(
    scope: CoroutineScope,
    downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
    offlineRepository: OfflineRepository,
    private val messenger: DownloadOutcomeMessenger,
) {
    /**
     * Ids whose quick actions flip to "Remove download". Sharing one
     * Eagerly-started flow across every host means one collector serves all
     * screens; the repository collapses equal id sets, so transfers don't
     * churn it.
     */
    val downloadedIds: StateFlow<Set<String>> =
        downloadRepository.observeDownloadedIdsIncludingSeries()
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val deleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
    )

    /** Long-press Download — see [DownloadIntake.startFromItem]. */
    suspend fun download(item: MediaItem): DownloadRequestResult =
        downloadIntake.startFromItem(item)

    /**
     * [download] plus the shared outcome handling: Started/Failed surface via
     * [DownloadOutcomeMessenger], both navigation outcomes (series selection,
     * richer detail flows) route to [onOpenDetail]. Hosts with richer routing
     * (pre-opened series sheet, local message queue) call [download] instead.
     */
    suspend fun downloadAndReport(item: MediaItem, onOpenDetail: (itemId: String) -> Unit) {
        when (val result = download(item)) {
            DownloadRequestResult.Started -> messenger.downloadStarted()
            is DownloadRequestResult.SeriesSelectionRequired -> onOpenDetail(result.seriesId)
            is DownloadRequestResult.NeedsDetailScreen -> onOpenDetail(result.itemId)
            is DownloadRequestResult.Failed -> messenger.downloadStartFailed()
        }
    }

    /**
     * Long-press Remove download — deletes the local download (artifacts +
     * offline rows) via the shared series-vs-item routing. Fire-and-forget.
     */
    fun removeDownload(item: MediaItem) {
        deleteActions.deleteDownload(item)
    }
}
