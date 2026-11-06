package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.model.OrientationMode
import kotlinx.coroutines.delay

/**
 * The player's orientation section (screen-root extract — issue #186): the
 * symmetric rotate toggle and the default-orientation lock effect. Extracted
 * from `VideoPlayerScreen.kt` when the effect gained the pre-settle intent
 * record (the screen root's total-line ceiling is a one-way guard; the
 * orientation section is the one that moved out).
 *
 * Semantics (issue #186 §4.7): the semantic intent is recorded BEFORE the
 * 400 ms settle so a fast PiP entry cannot lose it, and the window write
 * itself stays in the platform actual — which suppresses fixed writes while
 * the host is docked in PiP (the Issue-#11 guarantee).
 */

/** The symmetric rotate toggle (portrait ↔ the user's configured default landscape). */
@Composable
internal fun rememberOrientationToggle(
    windowOps: PlayerWindowOps,
    defaultOrientation: OrientationMode,
): () -> Unit = remember(windowOps, defaultOrientation) {
    {
        // Symmetric toggle lives in the platform actual: it reads the host's
        // current orientation to decide the direction.
        windowOps.toggleOrientation(
            preferLockedLandscape = defaultOrientation == OrientationMode.LOCKED_LANDSCAPE,
        )
    }
}

/**
 * Applies the screen's default-orientation decision: computes the lock,
 * records the semantic intent immediately (before any settle), then locks
 * after [OrientationLockDecision.SettleFirst]'s 400 ms window. Keyed on the
 * same inputs as the decision itself — no PiP key: the host decides whether
 * to write, so a PiP transition cannot recompose-storm this effect.
 */
@Composable
internal fun PlayerOrientationLockEffect(
    windowOps: PlayerWindowOps,
    isTv: Boolean,
    isCastConnected: Boolean,
    defaultOrientation: OrientationMode,
) {
    LaunchedEffect(isCastConnected, defaultOrientation) {
        val decision = orientationLockDecision(
            isTv = isTv,
            isCastConnected = isCastConnected,
            preference = defaultOrientation,
        )
        val lock = decision.lock
        // Record intent BEFORE the settle so a fast PiP entry cannot lose it:
        // the host's dock/restore stash starts from this semantic intent, and
        // an entry landing inside the 400 ms window would otherwise stash the
        // (still UNSPECIFIED) window request. lockOrientation records again
        // (idempotent) and suppresses the window write itself while the host
        // is docked — no isInPipMode key here: the host decides whether to
        // write, so a PiP transition cannot recompose-storm this effect.
        windowOps.recordOrientationIntent(lock)
        if (decision is OrientationLockDecision.SettleFirst) delay(400)
        windowOps.lockOrientation(lock)
    }
}
