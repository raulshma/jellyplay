package com.raulshma.jellyplay.core.ui.components
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_undo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * An optimistic, recoverable action surfaced via a snackbar with an "Undo" button.
 *
 * ViewModels emit these through a `Channel<UndoableAction>(Channel.BUFFERED)`
 * exposed as `Flow<UndoableAction>`; the screen collects them with
 * [CollectUndoActions] (or [UndoSnackbarOverlay] for the all-in-one variant).
 *
 * @param message Human-readable description, e.g. "Removed 'Pilot' from playlist".
 * @param onUndo Reverts the action. Runs on the main thread when the user taps
 * the action button. Capture the snapshot needed to revert at emit time — by the
 * time the snackbar expires the data may have shifted. Doubles as the one-action
 * callback for NON-undo snackbars (e.g. an announcement whose button navigates).
 * @param actionLabel Defaults to the shared `core_undo` ("Undo"); override only
 * for domain-specific verbs (e.g. "Restore").
 * @param duration Recoverable actions are not errors; keep them brief.
 * @param onDismiss Runs when the snackbar left WITHOUT its button tapped
 * (timeout or swipe); null = nothing to do. Never runs when the button was
 * tapped ([onUndo] covers that outcome), so an emit-side announce-once gate can
 * stamp "seen" on exactly one of the two terminal outcomes.
 */
data class UndoableAction(
    val message: String,
    val onUndo: () -> Unit,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val onDismiss: (() -> Unit)? = null,
)

/**
 * Collects [actions] into [snackbarHostState], invoking [action.onUndo] when the
 * user taps the action button. Use when the screen already owns a
 * [SnackbarHostState] (e.g. it also shows error messages).
 *
 * @param undoLabel The "Undo" action label; defaults to the shared string.
 */
@Composable
fun CollectUndoActions(
    actions: Flow<UndoableAction>,
    snackbarHostState: SnackbarHostState,
    undoLabel: String,
) {
    LaunchedEffect(Unit) {
        actions.collect { action ->
            val result = snackbarHostState.showSnackbar(
                message = action.message,
                actionLabel = action.actionLabel ?: undoLabel,
                duration = action.duration,
            )
            // Exactly one branch runs per snackbar: the button tap routes to
            // [UndoableAction.onUndo], every other exit (timeout, swipe) to the
            // optional [UndoableAction.onDismiss] — the announce-once pairing.
            if (result == SnackbarResult.ActionPerformed) {
                action.onUndo()
            } else {
                action.onDismiss?.invoke()
            }
        }
    }
}

/**
 * All-in-one overlay: renders a [SnackbarHost] pinned to the bottom of the
 * screen and collects [actions] into it. For screens that have no other
 * snackbar surface (e.g. Home, Playlists) this is the one-call wiring.
 *
 * Place inside the screen's root `Box`:
 *
 * ```
 * Box {
 * content()
 * UndoSnackbarOverlay(actions = viewModel.undoActions, modifier = Modifier.align(...))
 * }
 * ```
 */
@Composable
fun UndoSnackbarOverlay(
    actions: Flow<UndoableAction>,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.core_undo)
    CollectUndoActions(actions, snackbarHostState, undoLabel)
    JellyPlaySnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Convenience factory for the canonical undo channel. Returns the channel to
 * emit to (from the VM) and the flow to collect (in the screen):
 *
 * ```
 * private val _undoActions = undoActionChannel()
 * val undoActions = _undoActions.receiveAsFlow()
 * ```
 *
 * ViewModels should hold the channel as a `private val` and expose
 * `_undoActions.receiveAsFlow()`.
 */
fun undoActionChannel(): Channel<UndoableAction> = Channel(Channel.BUFFERED)
