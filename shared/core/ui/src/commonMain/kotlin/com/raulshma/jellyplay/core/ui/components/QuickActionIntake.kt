package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaQuickActionScope
import com.raulshma.jellyplay.core.model.quickActions

/**
 * What executing a quick action MEANS, as data: the routing table for every
 * grid/detail host screen, decided in one pure function
 * ([quickActionEffect]) so it is assertable — the same `when` used to be
 * hand-copied into a remembered composable lambda in eight screens where no
 * test could reach it. The home feature's `HomeQuickActionEffect` shape,
 * generalized: home keeps its own fold because its table is home-shaped
 * (series sheets, optimistic section patches).
 */
sealed interface QuickActionEffect {
    /** Hand the item to the host screen's play routing. */
    data class Play(val item: MediaItem) : QuickActionEffect

    /** Flip the item's watched flag (optimistic where the host supports it). */
    data class MarkPlayed(val item: MediaItem, val played: Boolean) : QuickActionEffect

    /** Start a download through the host's download intake. */
    data class Download(val item: MediaItem) : QuickActionEffect

    /** Queue the remove-download confirmation (owned by the intake host). */
    data class RemoveDownload(val item: MediaItem) : QuickActionEffect

    /** Open the host's detail routing for the item. */
    data class OpenDetail(val item: MediaItem) : QuickActionEffect

    /** Toggle the favorite flag (offered by the offline hosts). */
    data class ToggleFavorite(val item: MediaItem) : QuickActionEffect
}

/**
 * The quick-action routing table shared by every host screen (library,
 * favorites, studio, search, media/collection/person detail, offline
 * library): each action becomes an effect carrying the item; what the effect
 * MEANS is the host's [QuickActionAdapter]. Pure and public so the table is
 * pinned by [QuickActionIntakeTest] instead of being eyeballable only.
 *
 * Declared delta: `ADD_TO_PLAYLIST` folds onto [QuickActionEffect.OpenDetail].
 * The library grid — the only host that offers the action — always routed it
 * to the item's detail screen (the picker lives in feature/details, which
 * the library module does not depend on); the table now says so once.
 *
 * Which actions are OFFERED stays upstream in
 * [quickActions][com.raulshma.jellyplay.core.model.quickActions] (scope +
 * download-eligibility gates, pinned by `QuickActionsPolicyTest` in
 * core/model) — this fold is the execute side and trusts the resolver.
 */
fun quickActionEffect(item: MediaItem, action: QuickAction): QuickActionEffect = when (action) {
    QuickAction.PLAY -> QuickActionEffect.Play(item)
    QuickAction.MARK_WATCHED -> QuickActionEffect.MarkPlayed(item, played = true)
    QuickAction.MARK_UNWATCHED -> QuickActionEffect.MarkPlayed(item, played = false)
    QuickAction.DOWNLOAD -> QuickActionEffect.Download(item)
    QuickAction.REMOVE_DOWNLOAD -> QuickActionEffect.RemoveDownload(item)
    QuickAction.ADD_TO_PLAYLIST, QuickAction.DETAILS -> QuickActionEffect.OpenDetail(item)
    QuickAction.FAVORITE, QuickAction.UNFAVORITE -> QuickActionEffect.ToggleFavorite(item)
}

/**
 * A host screen's whole quick-action interaction surface — the per-screen
 * adapter over the shared [QuickActionEffect] table (the `HomeDockCallbacks`
 * shape). Each member receives the item the sheet was opened for; the
 * screens keep only their navigation/VM lambdas here, no dispatch logic.
 *
 * [onDownload] never fires on hosts that resolve without the download action
 * (the offline library); [onToggleFavorite] never fires on hosts that do not
 * offer the favorite toggle — both keep inert defaults.
 */
@Immutable
data class QuickActionAdapter(
    val onPlay: (MediaItem) -> Unit,
    val onOpenDetail: (MediaItem) -> Unit,
    val onMarkPlayed: (MediaItem, played: Boolean) -> Unit,
    val onDownload: (MediaItem) -> Unit = {},
    val onRemoveDownload: (MediaItem) -> Unit,
    val onToggleFavorite: (MediaItem) -> Unit = {},
)

/**
 * The one quick-action intake handle for a host screen: the
 * [MediaQuickActionController] (provide it via
 * [LocalMediaQuickActionController] so every card in scope wires its
 * long-press), the pending remove-download confirmation, and the TV focus
 * key. Constructed only by [rememberQuickActionIntake] (and tests).
 *
 * The TV Menu key handler is [openFocusedItem]: it shows the focused card's
 * sheet and reports whether a focused card existed — hosts that want to
 * consume the key unconditionally discard the flag (the seven grid hosts);
 * media-detail returns it so an unfocused Menu propagates.
 */
@Stable
class QuickActionIntake internal constructor(
    val controller: MediaQuickActionController,
    internal val removeDownloadState: RemoveDownloadState,
    internal val adapter: QuickActionAdapter,
    tvFocusedItemState: MutableState<MediaItem?>,
) {
    /**
     * TV-only: the card currently holding D-pad focus, so the Menu key can
     * open its quick actions. Written by the host's focus-tracking grids.
     */
    var tvFocusedItem: MediaItem? by tvFocusedItemState

    /**
     * TV Menu-key handler: open the focused card's quick actions. Returns
     * whether a focused card existed (see class KDoc for the two consume
     * contracts hosts build on this).
     */
    fun openFocusedItem(): Boolean {
        val focused = tvFocusedItem
        if (focused != null) controller.show(focused)
        return focused != null
    }
}

/**
 * Fold [action] onto [quickActionEffect] and dispatch it — the exact body of
 * the remembered execute lambda [rememberQuickActionIntake] wires into the
 * controller. Top-level so production and tests share one definition.
 */
internal fun executeQuickAction(
    item: MediaItem,
    action: QuickAction,
    adapter: QuickActionAdapter,
    removeDownloadState: RemoveDownloadState,
) {
    dispatchQuickActionEffect(quickActionEffect(item, action), adapter, removeDownloadState)
}

/**
 * The mechanical effect dispatch over [quickActionEffect] — the half every
 * hand-copied screen block used to inline. `RemoveDownload` queues the
 * shared confirmation (never deletes directly); everything else is the
 * adapter's.
 */
internal fun dispatchQuickActionEffect(
    effect: QuickActionEffect,
    adapter: QuickActionAdapter,
    removeDownloadState: RemoveDownloadState,
) {
    when (effect) {
        is QuickActionEffect.Play -> adapter.onPlay(effect.item)
        is QuickActionEffect.MarkPlayed -> adapter.onMarkPlayed(effect.item, effect.played)
        is QuickActionEffect.Download -> adapter.onDownload(effect.item)
        is QuickActionEffect.RemoveDownload -> removeDownloadState.request(effect.item)
        is QuickActionEffect.OpenDetail -> adapter.onOpenDetail(effect.item)
        is QuickActionEffect.ToggleFavorite -> adapter.onToggleFavorite(effect.item)
    }
}

/**
 * The stable shared "never downloaded" resolver: a top-level instance (NOT a
 * per-call-site lambda literal) so the [rememberQuickActionIntake] default
 * doesn't churn the remembered controller's keys on every recomposition —
 * an open sheet must survive unrelated recompositions, as it did when each
 * screen hand-copied the block.
 */
private val notDownloaded: (MediaItem) -> Boolean = { false }

/**
 * Remembers a screen-scoped [QuickActionIntake]: the sheet controller over
 * [quickActions] resolution (scope + eligibility flags + per-item
 * [isDownloaded]), the fold-to-adapter dispatch, the remove-download
 * confirmation and the TV focus key.
 *
 * Key the changing inputs yourself (e.g. `isDownloaded = remember(downloadedIds)
 * { ... }`, `adapter = remember(viewModel, onItemClick) { ... }`) — the
 * remembered controller is keyed on those lambda refs, so a state change
 * rebuilds the resolver exactly as the hand-copied blocks did (an open sheet
 * closes) while the focus key and a pending remove-download confirm survive
 * the rebuild, as before.
 */
@Composable
fun rememberQuickActionIntake(
    scope: MediaQuickActionScope,
    adapter: QuickActionAdapter,
    isDownloaded: (MediaItem) -> Boolean = notDownloaded,
    includeDownload: Boolean = false,
    includeAddToPlaylist: Boolean = false,
    includeRemoveDownload: Boolean = false,
    includeFavorite: Boolean = false,
): QuickActionIntake {
    // The two long-lived pieces are keyless remembers: a resolve/adapter
    // rebuild swaps the controller without resetting the TV focus key or
    // dismissing a pending remove-download confirmation.
    val removeDownloadState = remember { RemoveDownloadState() }
    val tvFocusedItemState = remember { mutableStateOf<MediaItem?>(null) }
    val resolveActions = remember(
        scope,
        isDownloaded,
        includeDownload,
        includeAddToPlaylist,
        includeRemoveDownload,
        includeFavorite,
    ) {
        { item: MediaItem ->
            item.quickActions(
                scope,
                includeDownload = includeDownload,
                includeAddToPlaylist = includeAddToPlaylist,
                includeRemoveDownload = includeRemoveDownload,
                includeFavorite = includeFavorite,
                isDownloaded = isDownloaded(item),
            )
        }
    }
    val controller = rememberMediaQuickActionController(
        resolveActions = resolveActions,
        executeAction = remember(adapter) {
            { item: MediaItem, action: QuickAction ->
                executeQuickAction(item, action, adapter, removeDownloadState)
            }
        },
    )
    return QuickActionIntake(controller, removeDownloadState, adapter, tvFocusedItemState)
}

/**
 * Renders the [MediaQuickActionHost] and the remove-download confirmation for
 * [intake]. Place once at the screen root — this replaces the per-screen
 * `MediaQuickActionHost` + `RemoveDownloadConfirmHost` pair. The confirm
 * routes to the adapter's [QuickActionAdapter.onRemoveDownload]; quick-action
 * removal only ever deletes the local download — the server copy is
 * untouched, and the dialog message says so.
 */
@Composable
fun QuickActionIntakeHost(intake: QuickActionIntake) {
    MediaQuickActionHost(intake.controller)
    RemoveDownloadConfirmHost(
        state = intake.removeDownloadState,
        onConfirmRemove = intake.adapter.onRemoveDownload,
    )
}
