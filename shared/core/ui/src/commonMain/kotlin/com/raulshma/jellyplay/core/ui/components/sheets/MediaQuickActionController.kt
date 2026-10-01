package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Screen-scoped host for [MediaQuickActionSheet]
 *
 * The sheet needs screen-specific data (which actions apply to an item, and
 * what each action does — play/mark-watched/add-to-playlist navigate through
 * that screen's VM), so the state cannot live app-wide. Each screen that wants
 * quick actions remembers one controller, provides it via
 * [LocalMediaQuickActionController] so every [PosterCard] in scope wires its
 * long-press to [show], renders [MediaQuickActionHost] once, and — on TV —
 * forwards the focused item + Menu key to [show].
 *
 * The `resolveActions`/`executeAction` lambdas should be `remember`-ed by the
 * caller so the controller survives recomposition (the lambdas are keys).
 */
@Stable
class MediaQuickActionController(
    private val resolveActions: (MediaItem) -> List<QuickAction>,
    private val executeAction: (MediaItem, QuickAction) -> Unit,
) {
    private val _currentItem = MutableStateFlow<MediaItem?>(null)

    /** The item whose quick actions are showing, or `null` when the sheet is closed. */
    val currentItem: StateFlow<MediaItem?> = _currentItem.asStateFlow()

    /** Open the quick-action sheet for [item]. Replaces any open sheet. */
    fun show(item: MediaItem) {
        _currentItem.value = item
    }

    /** Close the sheet. */
    fun hide() {
        _currentItem.value = null
    }

    /** Which actions apply to [item], resolved by the host screen. */
    fun actionsFor(item: MediaItem): List<QuickAction> = resolveActions(item)

    /** Execute [action] for [item]; the host screen performs the work. */
    fun execute(item: MediaItem, action: QuickAction) {
        executeAction(item, action)
    }
}

/**
 * CompositionLocal exposing the nearest screen's [MediaQuickActionController].
 * `null` by default so cards in unwired contexts (tests, previews, screens
 * without quick actions) keep their existing long-press behavior.
 */
val LocalMediaQuickActionController = staticCompositionLocalOf<MediaQuickActionController?> { null }

/**
 * Remembers a [MediaQuickActionController]. Key the lambdas yourself (e.g.
 * `remember(viewModel) { ... }`) so the controller is stable across
 * recompositions — recreating it would close any open sheet.
 */
@Composable
fun rememberMediaQuickActionController(
    resolveActions: (MediaItem) -> List<QuickAction>,
    executeAction: (MediaItem, QuickAction) -> Unit,
): MediaQuickActionController = remember(resolveActions, executeAction) {
    MediaQuickActionController(resolveActions, executeAction)
}

/**
 * Renders the [MediaQuickActionSheet] for [controller]'s current item. Place
 * once at the screen root (outside the card content so opening the sheet
 * doesn't recompose the list). Closes itself if the item resolves to zero
 * actions.
 */
@Composable
fun MediaQuickActionHost(controller: MediaQuickActionController) {
    val item by controller.currentItem.collectAsStateWithLifecycle()
    val target = item ?: return
    val actions = remember(target) { controller.actionsFor(target) }
    if (actions.isEmpty()) {
        LaunchedEffect(target) { controller.hide() }
        return
    }
    MediaQuickActionSheet(
        actions = actions,
        title = target.name,
        onAction = { action ->
            controller.hide()
            controller.execute(target, action)
        },
        onDismiss = controller::hide,
    )
}
