package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.repository.DownloadCoverage
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The download/remove half of a long-press quick-action menu, shared by every
 * host surface (library, favorites, search, studio, detail rows — issue #147:
 * "download/delete options on the press-and-hold menu" everywhere).
 *
 * Owns exactly the two stateless halves the screens kept re-wiring per
 * ViewModel: the downloaded-id set that flips a card's DOWNLOAD slot to
 * REMOVE_DOWNLOAD ([downloadedIds] — completed ids ∪ series ids, the union
 * contract on [DownloadRepository.downloadCoverage]), and
 * the delete routing ([removeDownload] — series cards delete the whole series
 * download, anything else the single item; never touches the server).
 *
 * [download] is suspend and outcome-returning on purpose: hosts with richer
 * routing (`DetailViewModel` owns a local message queue) branch on it
 * themselves. Every other host uses [downloadAndReport], which folds the
 * shared cascade in here: Started/Failed surface through the injected or
 * per-call [DownloadOutcomeMessenger] (UserMessageBus lives in core/ui, which
 * core/data must not depend on) and the navigation outcomes route to the
 * host's open-detail callback under the per-host `seriesOpensSheet` flag.
 *
 * Koin-owned construction (jvmShared convention): no @Inject/@Singleton —
 * DataKoinModule wires the process scope and dependencies, mirroring the
 * other moved repositories.
 *
 * Since the promoted-interface pass this class IS the JVM
 * [QuickDownloadActions]: it implements the commonMain interface directly
 * (the DownloadIntake precedent — the surface crosses verbatim, so no
 * verbatim-forward adapter is needed) and dataJvmModule binds the interface
 * over this single. `isSupported` is constant `true` here: the class only
 * exists where the JVM download engine does.
 */
class MediaDownloadActions(
    scope: CoroutineScope,
    downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
    offlineRepository: OfflineRepository,
    private val messenger: DownloadOutcomeMessenger,
) : QuickDownloadActions {

    /** The JVM download pipeline is always present — see the class KDoc. */
    override val isSupported: Boolean = true

    /**
     * Ids whose quick actions flip to "Remove download". Sharing one
     * Eagerly-started flow across every host means one collector serves all
     * screens; the repository collapses equal id sets, so transfers don't
     * churn it.
     */
    override val downloadedIds: StateFlow<Set<String>> =
        downloadRepository.downloadCoverage()
            .map { it.ids }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val deleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
    )

    /** Long-press Download — see [DownloadIntake.startFromItem]. */
    override suspend fun download(item: MediaItem): DownloadRequestResult =
        downloadIntake.startFromItem(item)

    /**
     * [download] plus the shared outcome handling: Started/Failed surface via
     * the injectable [messenger] (null ⇒ the platform default messenger),
     * NeedsDetailScreen routes plainly, SeriesSelectionRequired rides the
     * per-host `seriesOpensSheet` flag (or is silently skipped when null —
     * see [QuickDownloadActions.downloadAndReport]). Hosts with richer
     * routing still call [download] and branch on the result themselves.
     */
    override suspend fun downloadAndReport(
        item: MediaItem,
        onOpenDetail: (itemId: String, prePresentDownloadSheet: Boolean) -> Unit,
        seriesOpensSheet: Boolean?,
        messenger: DownloadOutcomeMessenger?,
    ) {
        val sink = messenger ?: this.messenger
        when (val result = download(item)) {
            DownloadRequestResult.Started -> sink.downloadStarted()
            is DownloadRequestResult.SeriesSelectionRequired ->
                if (seriesOpensSheet != null) onOpenDetail(result.seriesId, seriesOpensSheet)
            is DownloadRequestResult.NeedsDetailScreen -> onOpenDetail(result.itemId, false)
            is DownloadRequestResult.Failed -> sink.downloadStartFailed()
        }
    }

    /**
     * Long-press Remove download — deletes the local download (artifacts +
     * offline rows) via the shared series-vs-item routing. Fire-and-forget.
     */
    override fun removeDownload(item: MediaItem) {
        deleteActions.deleteDownload(item)
    }
}
