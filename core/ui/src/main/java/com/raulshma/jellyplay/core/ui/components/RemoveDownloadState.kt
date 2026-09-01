package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Shared pending-removal state for quick-action hosts (library, favorites,
 * search, studio, detail rows — issue #147): the REMOVE_DOWNLOAD arm calls
 * [request], and one [RemoveDownloadConfirmHost] render at the screen root
 * shows the [RemoveDownloadConfirmDialog]. Hoisted holder (not per-item
 * state) so the dialog survives its card leaving composition while open.
 */
@Stable
class RemoveDownloadState {
    /** The item awaiting a remove-download confirm, or `null` when idle. */
    var pending: MediaItem? by mutableStateOf<MediaItem?>(null)
        private set

    /** Queue [item] for the remove-download confirmation. */
    fun request(item: MediaItem) {
        pending = item
    }

    /** Dismiss the pending confirmation (also called by the host on close). */
    fun clear() {
        pending = null
    }
}

/** Remembers a screen-scoped [RemoveDownloadState]. */
@Composable
fun rememberRemoveDownloadState(): RemoveDownloadState = remember { RemoveDownloadState() }

/**
 * Renders [RemoveDownloadConfirmDialog] for [state]'s pending item; no-op
 * when idle. Place once at the screen root, next to [MediaQuickActionHost].
 * Quick-action removal only ever deletes the local download — the server
 * copy is untouched, and the dialog message says so.
 */
@Composable
fun RemoveDownloadConfirmHost(
    state: RemoveDownloadState,
    onConfirmRemove: (MediaItem) -> Unit,
) {
    state.pending?.let { target ->
        RemoveDownloadConfirmDialog(
            item = target,
            onConfirm = {
                onConfirmRemove(target)
                state.clear()
            },
            onDismiss = { state.clear() },
        )
    }
}
