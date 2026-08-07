package com.raulshma.jellyplay.core.ui.components

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.R
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
 * "Undo". Capture the snapshot needed to revert at emit time — by the time the
 * snackbar expires the data may have shifted.
 * @param actionLabel Defaults to the shared `core_undo` ("Undo"); override only
 * for domain-specific verbs (e.g. "Restore").
 * @param duration Recoverable actions are not errors; keep them brief.
 */
data class UndoableAction(
    val message: String,
    val onUndo: () -> Unit,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
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
            if (result == SnackbarResult.ActionPerformed) {
                action.onUndo()
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
    val undoLabel = stringResource(R.string.core_undo)
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
