package com.raulshma.jellyplay.feature.downloads

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus

/**
 * The download operations the downloads screen offers as a single fold. One
 * enum entry per repository dispatch shape (pause / resume+enqueue / cancel /
 * retry+enqueue / delete), so the screen's action bar, the app-bar global
 * actions, and the per-row buttons all speak the same vocabulary instead of
 * hand-rolled per-button predicates.
 */
enum class DownloadBulkAction {
    PAUSE,
    RESUME,
    CANCEL,
    RETRY_FAILED,
    DELETE,
}

/**
 * Which downloads an action applies to:
 *  - [Item] — a single row action (admission-gated like the bulk family);
 *  - [Selected] — the current selection mode batch;
 *  - [All] — the whole list, without entering selection mode.
 */
sealed interface DownloadActionScope {
    data class Item(val id: String) : DownloadActionScope
    data object Selected : DownloadActionScope
    data object All : DownloadActionScope
}

/**
 * Pure write-algebra for [DownloadBulkAction]: the admission table (which
 * [DownloadStatus] values an action may act on) and the target fold
 * (which concrete items a scope admits). Derived verbatim from the former
 * hand-written filter lambdas in [DownloadsViewModel] — no new semantics:
 *
 *  - PAUSE        admits DOWNLOADING;
 *  - RESUME       admits PAUSED;
 *  - CANCEL       admits PENDING, QUEUED, DOWNLOADING, PAUSED;
 *  - RETRY_FAILED admits FAILED;
 *  - DELETE       admits every status (a delete frees disk regardless).
 *
 * The VM dispatches exactly what [targets] returns; the screen enables a
 * control exactly when [supports] is true, so neither re-encodes the table.
 */
object DownloadActions {

    // null = every status is admissible (DELETE).
    private val admittingStatuses: Map<DownloadBulkAction, Set<DownloadStatus>?> = mapOf(
        DownloadBulkAction.PAUSE to setOf(DownloadStatus.DOWNLOADING),
        DownloadBulkAction.RESUME to setOf(DownloadStatus.PAUSED),
        DownloadBulkAction.CANCEL to setOf(
            DownloadStatus.PENDING,
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
        ),
        DownloadBulkAction.RETRY_FAILED to setOf(DownloadStatus.FAILED),
        DownloadBulkAction.DELETE to null,
    )

    /**
     * True when [targets] is non-empty — the screen's "never offer a no-op"
     * rule, folded once instead of per-button `any { ... }` predicates.
     */
    fun supports(
        action: DownloadBulkAction,
        downloads: List<DownloadItem>,
        selectedIds: Set<String>,
        scope: DownloadActionScope,
    ): Boolean = targets(action, downloads, selectedIds, scope).isNotEmpty()

    /**
     * The items [action] would act on: intersect [scope]'s candidate set with
     * the live list (a selection that outlived its rows yields nothing), then
     * keep only statuses the admission table accepts, preserving list order.
     */
    fun targets(
        action: DownloadBulkAction,
        downloads: List<DownloadItem>,
        selectedIds: Set<String>,
        scope: DownloadActionScope,
    ): List<DownloadItem> {
        val admitted = admittingStatuses.getValue(action)
        fun admits(item: DownloadItem) = admitted == null || item.status in admitted
        return when (scope) {
            is DownloadActionScope.Item -> downloads.filter { it.id == scope.id && admits(it) }
            DownloadActionScope.Selected -> downloads.filter { it.id in selectedIds && admits(it) }
            DownloadActionScope.All -> downloads.filter(::admits)
        }
    }
}
