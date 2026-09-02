package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.QuickAction

/**
 * What executing a home card quick action MEANS, as data: the routing table
 * (series vs movie vs other) decided in one pure function
 * ([homeQuickActionEffect]) so it is assertable — the same `when` used to
 * live inline in a remembered composable lambda where no test could reach
 * it. The screen's execute lambda reduces to a mechanical effect dispatch.
 */
internal sealed interface HomeQuickActionEffect {
    /** Hand the item to the shared play funnel (series go through smart-play). */
    data class Play(val item: MediaItem) : HomeQuickActionEffect

    /** Optimistically flip played/unplayed across every section. */
    data class MarkPlayed(val item: MediaItem, val played: Boolean) : HomeQuickActionEffect

    /** Open the unified detail tree for the item. */
    data class ShowDetails(val item: MediaItem) : HomeQuickActionEffect

    /** Open the series download sheet right here on home. */
    data class OpenSeriesDownloadSheet(val series: MediaItem) : HomeQuickActionEffect

    /**
     * Start an inline single-stream download; series and other non-inline
     * types are routed to the detail screen via [onOpenDetail].
     */
    data class StartDownload(
        val item: MediaItem,
        val onOpenDetail: (itemId: String, openDownloadSheet: Boolean) -> Unit,
    ) : HomeQuickActionEffect

    /** Open the advanced delete-episodes sheet (series). */
    data class OpenSeriesDeleteSheet(val series: MediaItem) : HomeQuickActionEffect

    /** Ask for confirmation before deleting a non-series download. */
    data class ConfirmDeleteDownload(val item: MediaItem) : HomeQuickActionEffect

    /** Action not handled on home (e.g. favourite toggles live elsewhere). */
    data object None : HomeQuickActionEffect
}

/**
 * The home's quick-action routing table. SERIES cards: Download opens the
 * series sheet (season/episode selection), Remove-download opens the
 * delete-episodes sheet. Everything else: Download starts inline (falling
 * back to the detail screen for non-inline types via the intake result),
 * Remove-download asks for confirmation. Pure and internal so the table is
 * pinned by [HomeQuickActionsTest] instead of being eyeballable only.
 */
internal fun homeQuickActionEffect(
    item: MediaItem,
    action: QuickAction,
    onOpenDetail: (itemId: String, openDownloadSheet: Boolean) -> Unit,
): HomeQuickActionEffect = when (action) {
    QuickAction.PLAY -> HomeQuickActionEffect.Play(item)
    QuickAction.MARK_WATCHED -> HomeQuickActionEffect.MarkPlayed(item, played = true)
    QuickAction.MARK_UNWATCHED -> HomeQuickActionEffect.MarkPlayed(item, played = false)
    QuickAction.DETAILS -> HomeQuickActionEffect.ShowDetails(item)
    QuickAction.DOWNLOAD ->
        if (item.mediaType == MediaType.SERIES) {
            HomeQuickActionEffect.OpenSeriesDownloadSheet(item)
        } else {
            HomeQuickActionEffect.StartDownload(item, onOpenDetail)
        }
    QuickAction.REMOVE_DOWNLOAD ->
        if (item.mediaType == MediaType.SERIES) {
            HomeQuickActionEffect.OpenSeriesDeleteSheet(item)
        } else {
            HomeQuickActionEffect.ConfirmDeleteDownload(item)
        }
    else -> HomeQuickActionEffect.None
}
