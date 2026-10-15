package com.raulshma.jellyplay.core.data.download

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal message sink so core/data can report download outcomes without
 * depending on core/ui's UserMessageBus.
 */
interface DownloadOutcomeMessenger {
    fun downloadStarted()
    fun downloadStartFailed()
}

/**
 * The quick-action download/remove seam every host surface shares (library,
 * favorites, search, studio, home). Originally shipped as byte-identical
 * `internal` twins in feature:library and feature:search (one landed by copy,
 * the other by paste); hoisted here so the contract has one home beside the
 * [MediaDownloadActions] delegate it wraps and the
 * [DownloadRequestResult] vocabulary it returns.
 *
 * The JVM actual IS [MediaDownloadActions] — the jvmShared single implements
 * this interface directly (promotion per the DownloadIntake precedent: the
 * surface is core:model + [DownloadRequestResult] only, so no verbatim-
 * forward adapter is needed) and dataJvmModule binds the interface over that
 * single (same messenger routing, same shared downloadedIds flow —
 * android/desktop behavior unchanged).
 *
 * IDIOM RULE (declared with the download-actions seam consolidation,
 * tightened by the promoted-interface pass): a download read a feature needs
 * is DECLARED here in core:data commonMain and IMPLEMENTED AND BOUND by
 * core:data on both platforms — directly by the owning jvmShared
 * single/engine where the surface crosses verbatim, per the DownloadIntake
 * precedent; never through a per-read adapter triplet again. Features never
 * grow their own wall-crossing template (the deleted feature twins this
 * replaced were exactly that mistake).
 */
interface QuickDownloadActions {

    /** Whether this platform has a download pipeline; gates download CTAs. */
    val isSupported: Boolean

    /**
     * Ids whose quick actions flip to "Remove download" — completed downloads
     * ∪ series ids (the union contract of MediaDownloadActions.downloadedIds).
     */
    val downloadedIds: StateFlow<Set<String>>

    /** Start a download for [item] (see [MediaDownloadActions.download]). */
    suspend fun download(item: MediaItem): DownloadRequestResult

    /**
     * [download] plus the shared outcome handling: Started/Failed surface via
     * [messenger] (null ⇒ the platform default), NeedsDetailScreen routes to
     * [onOpenDetail] plainly, and SeriesSelectionRequired routes to
     * [onOpenDetail] with the series sheet pre-presented iff
     * [seriesOpensSheet] — plainly when false (the hosts whose navigation
     * cannot pre-present the sheet), or not at all when `null` (home, whose
     * series cards are intercepted upstream by the in-place series sheet, so
     * the branch is unreachable and pinned as a silent no-op).
     */
    suspend fun downloadAndReport(
        item: MediaItem,
        onOpenDetail: (itemId: String, prePresentDownloadSheet: Boolean) -> Unit,
        seriesOpensSheet: Boolean? = false,
        messenger: DownloadOutcomeMessenger? = null,
    )

    /** Remove [item]'s local download (artifacts + offline rows). Fire-and-forget. */
    fun removeDownload(item: MediaItem)
}

/**
 * The quick-action half of the `PagedMediaGridHost` constructor, wired once:
 * the six seams (`userDataChanges` / `downloadedIds` / `downloadSupported` /
 * `setPlayedSilently` / `requestDownload` / `removeDownloadItem`) that four
 * grid view models (library, favorites, studio detail, search) each hand-typed
 * identically from the same three collaborators — the repository's change
 * feed, the mutator's silent default, and the shared [QuickDownloadActions]
 * with the host's [DownloadOutcomeMessenger] pre-bound. Build with
 * [pagedGridQuickActions]; the caller spreads the members into the host
 * verbatim and keeps only its own paged source (and the genuinely per-screen
 * deltas: the library's sink and its `seriesOpensSheet = true` routing).
 *
 * Lives in core:data because the wiring is entirely core:data vocabulary; the
 * host itself cannot absorb it (core/ui owns no core:data types), so the
 * bundle crosses as plain flows and lambdas — the host's parameter surface
 * is unchanged.
 */
class PagedGridQuickActions(
    /** The user-data change feed driving the host's deferred refresher. */
    val userDataChanges: Flow<UserDataChange>,
    /** The shared quick-action id set — see [QuickDownloadActions.downloadedIds]. */
    val downloadedIds: StateFlow<Set<String>>,
    /** Whether this platform has a download pipeline — see [QuickDownloadActions.isSupported]. */
    val downloadSupported: Boolean,
    /** The silent mark-played fold — the mutator's [UserDataMutator.setPlayed] silent default. */
    val setPlayedSilently: suspend (itemId: String, played: Boolean) -> Unit,
    /**
     * The [QuickDownloadActions.downloadAndReport] fold with the factory's
     * [messenger][DownloadOutcomeMessenger] pre-bound.
     */
    val requestDownload: suspend (
        item: MediaItem,
        onOpenDetail: (itemId: String, prePresentDownloadSheet: Boolean) -> Unit,
        seriesOpensSheet: Boolean,
    ) -> Unit,
    /** The remove-download routing — see [QuickDownloadActions.removeDownload]. */
    val removeDownloadItem: (item: MediaItem) -> Unit,
)

/**
 * Wires [PagedGridQuickActions] from the collaborators every paged-grid host
 * shares. [messenger] is the host's download-outcome sink (null ⇒ the
 * [QuickDownloadActions] platform default, the shape of the hosts whose
 * strings live nowhere — favorites, studio, search).
 */
fun pagedGridQuickActions(
    mediaRepository: MediaRepository,
    userDataMutator: UserDataMutator,
    quickDownloadActions: QuickDownloadActions,
    messenger: DownloadOutcomeMessenger? = null,
): PagedGridQuickActions = PagedGridQuickActions(
    userDataChanges = mediaRepository.userDataChanges,
    downloadedIds = quickDownloadActions.downloadedIds,
    downloadSupported = quickDownloadActions.isSupported,
    setPlayedSilently = { itemId, played -> userDataMutator.setPlayed(itemId, played) },
    requestDownload = { item, onOpenDetail, seriesOpensSheet ->
        quickDownloadActions.downloadAndReport(item, onOpenDetail, seriesOpensSheet, messenger)
    },
    removeDownloadItem = quickDownloadActions::removeDownload,
)
