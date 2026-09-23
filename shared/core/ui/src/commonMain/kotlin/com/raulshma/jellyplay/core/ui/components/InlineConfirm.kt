package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single auto-reset window for inline flip-to-confirm buttons. Every
 * "destructive button flips to `Confirm?` and reverts on its own" call site
 * shares this one policy constant (the three former hand-rolled copies all
 * used the same 3 s literal).
 */
const val DEFAULT_INLINE_CONFIRM_TIMEOUT_MS: Long = 3_000L

/**
 * Holds one inline flip-to-confirm machine for a destructive button: the
 * first tap arms ("Confirm?"), the second tap within the window fires the
 * action, and arming auto-resets after [DEFAULT_INLINE_CONFIRM_TIMEOUT_MS].
 *
 * This is the button-flavoured counterpart of [ConfirmState] (the dialog
 * holder). Where [ConfirmState] defers a lambda into a dialog, this holder
 * keeps a plain armed flag the call site renders against — the button label
 * flips between its resting and confirm strings while [isConfirming] holds.
 *
 * ## Semantics (matching the former per-call-site `LaunchedEffect` copies)
 *
 * - [arm] flips to confirming and starts the countdown. Re-arming restarts
 *   a fresh full window (a stale countdown can never fire early).
 * - [confirm] runs its action only while armed, disarming first — so a
 *   throwing action still leaves the state disarmed.
 * - [reset] disarms without running anything (used on disarm paths; the
 *   timeout itself lands here too).
 *
 * The countdown runs on the [CoroutineScope] supplied by
 * [rememberInlineConfirm] (`rememberCoroutineScope`, i.e. cancelled with the
 * composition — the same lifetime the former `LaunchedEffect(flag)` copies
 * rode). Reset-on-content-change is a caller concern: key the
 * [rememberInlineConfirm] call on the item identity
 * (`rememberInlineConfirm(request.id)`), exactly as the former
 * `remember(request.id) { mutableStateOf(false) }` flags were keyed.
 *
 * Usage:
 *
 * ```
 * val deleteConfirm = rememberInlineConfirm(request.id)
 * Button(
 *     onClick = {
 *         if (deleteConfirm.isConfirming) deleteConfirm.confirm(onDelete)
 *         else deleteConfirm.arm()
 *     },
 * ) {
 *     Text(stringResource(if (deleteConfirm.isConfirming) confirmLabel else deleteLabel))
 * }
 * ```
 */
@Stable
class InlineConfirmState internal constructor(
    private val timeoutMs: Long,
    private val scope: CoroutineScope,
) {
    /** Whether the button currently shows its confirm label. */
    var isConfirming: Boolean by mutableStateOf(false)
        private set

    private var countdown: Job? = null

    /**
     * Flip to the confirm label and start the auto-reset countdown.
     * Cancels any stale countdown first so a re-arm always gets a full window.
     */
    fun arm() {
        countdown?.cancel()
        isConfirming = true
        countdown = scope.launch {
            delay(timeoutMs)
            isConfirming = false
        }
    }

    /**
     * Fire the destructive action if (and only if) armed; disarms first so the
     * label snaps back and the countdown is reaped. A no-op while unarmed.
     */
    fun confirm(onConfirm: () -> Unit) {
        if (!isConfirming) return
        countdown?.cancel()
        countdown = null
        isConfirming = false
        onConfirm()
    }

    /** Disarm without running anything (explicit reset; also the timeout path). */
    fun reset() {
        countdown?.cancel()
        countdown = null
        isConfirming = false
    }
}

/**
 * Remember an [InlineConfirmState] scoped to the composition.
 *
 * Pass the item identity as [inputs] (e.g. `rememberInlineConfirm(request.id)`)
 * so recycling the composable for a different item starts disarmed — the same
 * keying contract the former `remember(request.id) { mutableStateOf(false) }`
 * flags followed. [timeoutMs] is the only policy knob; the default is the
 * shared [DEFAULT_INLINE_CONFIRM_TIMEOUT_MS].
 */
@Composable
fun rememberInlineConfirm(
    vararg inputs: Any?,
    timeoutMs: Long = DEFAULT_INLINE_CONFIRM_TIMEOUT_MS,
): InlineConfirmState {
    val scope = rememberCoroutineScope()
    return remember(*inputs) { InlineConfirmState(timeoutMs, scope) }
}
