package com.raulshma.jellyplay.feature.player.video.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * The cold-start restore prompt's dialog, shared by both shells (the Android
 * `SessionRestoreHost` and the desktop `DesktopNavScaffold`) — the one home
 * for the prompt's literal texts and its arm-splitting choreography.
 * [ConfirmDialog] invokes the caller's dismiss after the confirm arm too, so
 * the dialog clears itself on both exits; an internal flag splits the arms so
 * only a REAL dismiss runs [onDismissed] (the handled-offer marker write —
 * see [SessionRestorationController.onAskDismissed]).
 *
 * The strings are literal English deliberately: the shells' one-shot texts
 * have no shared resource home (desktop has no Android res — the
 * DesktopLinkOpenMessages precedent).
 */
@Composable
fun SessionRestorePromptDialog(
    snapshot: SessionRestorationSnapshot,
    /** The confirm arm: mark the restore handled and navigate to the player. */
    onResume: () -> Unit,
    /** The real-dismiss side effect: persist the handled-offer marker. */
    onDismissed: () -> Unit,
    /** Runs on BOTH exits — the caller clears its prompt state here. */
    onDismiss: () -> Unit,
) {
    var confirmed by remember(snapshot) { mutableStateOf(false) }
    ConfirmDialog(
        title = "Resume \"${snapshot.title}\"?",
        message = "Continue where you left off.",
        confirmText = "Resume",
        dismissText = "Dismiss",
        tone = ConfirmTone.PRIMARY,
        onConfirm = {
            confirmed = true
            onResume()
        },
        onDismiss = {
            val real = !confirmed
            onDismiss()
            if (real) onDismissed()
        },
    )
}

/** The snapshot's player entry point: the resume position as route ticks. */
fun SessionRestorationSnapshot.toPlayerRoute(): Route =
    Route.VideoPlayer(itemId = itemId, startPositionTicks = positionMs * 10_000)
