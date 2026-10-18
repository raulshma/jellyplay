package com.raulshma.jellyplay.core.data.receiver

import android.content.BroadcastReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The ONE goAsync scope-window idiom. Runs [work] on a scope created for THIS
 * broadcast, keeping the broadcast alive ([BroadcastReceiver.goAsync]) until
 * the work completes, then finishes the pending result and tears the scope
 * down — the shape three receivers used to hand-roll as drifted private
 * `launchPending` copies (NotificationActionReceiver, DownloadActionReceiver,
 * JellyPlayUnifiedPushReceiver; the widget family's skeleton keeps its own
 * variant by ratified exception — widget action strings stay pair-local and
 * so does its window). The scope dies with the broadcast window: no
 * per-instance scope survives between broadcasts (or, worse, a reuse of a
 * receiver instance after an earlier broadcast's finally cancelled its single
 * scope).
 *
 * Throwable policy — catches-and-logs EVERYWHERE (rethrowing
 * [CancellationException] so cancellation still propagates and the finally
 * still finishes the window): a broadcast callback is best-effort live
 * signal — the inbox/messages surfaces, the downloads screen and the sync
 * engine are the durable systems of record — so one failed broadcast must
 * never take the process down. This deliberately widens the UnifiedPush
 * copy's long-standing catch-and-log to the other two receivers, whose
 * copies previously let a throw crash the process; that drift (not the
 * crashing) was the accident — a resume racing a cancelled worker is exactly
 * the kind of throw a shade-button broadcast should shrug off. [tag] scopes
 * the warning log to the calling receiver.
 */
fun BroadcastReceiver.runWithBroadcastScope(
    tag: String,
    work: suspend () -> Unit,
) {
    val pendingResult = goAsync()
    val broadcastScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    broadcastScope.launch {
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.w(tag, "broadcast work failed", t)
        } finally {
            pendingResult.finish()
            broadcastScope.cancel()
        }
    }
}
