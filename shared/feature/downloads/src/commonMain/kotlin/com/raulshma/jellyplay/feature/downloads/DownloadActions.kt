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
 * [DownloadStatus] values an action may act on), the target fold (which
 * concrete items a scope admits), the per-item availability fold the row
 * renderer consumes, and the COMPLETED-is-playable fold:
 *
 *  - PAUSE        admits DOWNLOADING;
 *  - RESUME       admits PAUSED;
 *  - CANCEL       admits PENDING, QUEUED, DOWNLOADING, PAUSED;
 *  - RETRY_FAILED admits FAILED;
 *  - DELETE       admits every status (a delete frees disk regardless).
 *
 * The VM dispatches exactly what [targets] returns; the screen enables a
 * control exactly when [supports] is true; the row renders exactly what
 * [availableActions] admits — so neither re-encodes the table.
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
     * The actions the admission table admits for [item], in declaration order
     * — the single source the row renderer consults for which actions EXIST
     * for a status. Per-status placement (which slot a button renders in,
     * icon, order) stays row code keyed on this set; only the admission is
     * module-owned, so a table change re-dresses rows without touching them.
     */
    fun availableActions(item: DownloadItem): List<DownloadBulkAction> =
        DownloadBulkAction.entries.filter { action ->
            admittingStatuses.getValue(action)?.let { item.status in it } ?: true
        }

    /**
     * The COMPLETED-is-playable fold: a download backs offline playback only
     * once its artifact set is complete — the single gate behind the row's
     * tap-to-detail and Play actions, the stream-instead offer, and the group
     * header's play target. Connectivity is deliberately NOT part of this
     * fold: streaming-instead is a presentation concern (the row's stream
     * entry drops while offline) and the gate stays at the call site — only
     * the COMPLETED half is module vocabulary.
     */
    fun isPlayable(item: DownloadItem): Boolean = item.status == DownloadStatus.COMPLETED

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
