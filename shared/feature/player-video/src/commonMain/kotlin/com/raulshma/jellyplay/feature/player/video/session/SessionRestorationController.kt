package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.SessionRestoreMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The shell-side half of the cold-start session restoration: ONE evaluation
 * per process, the decision application left entirely to the calling shell.
 *
 * Constructor-lambda controller (the migrated-controllers idiom — zero
 * uiState-type references, no Compose): the shell (or its Koin graph) supplies
 *
 *  - [restoreMode] — the persisted [SessionRestoreMode] read
 *    (`PlaybackStore.playback.value.sessionRestoreMode` on both shells);
 *  - [fetchLatestSnapshot] — the server-authoritative resume read, ONE
 *    repository/client call mapped through [latestResumeVideoSnapshot] and
 *    cached nowhere (the design keeps this feature free of new local storage
 *    for resume DATA; incognito sessions simply have no server resume data to
 *    find — see [SessionRestorationSnapshot]);
 *  - [readDismissal] / [persistDismissal] — the ONE local-storage exception,
 *    the prompt's handled-offer marker (`PlaybackStore.sessionRestoreDismissalNow`
 *    / `setSessionRestoreDismissal` on both shells): the fingerprint of the
 *    exact offer the user dismissed, so the next cold start does not re-ask
 *    (see [SessionRestoreDismissal]; the player's teardown stop report
 *    writes the same key for the played-through arm).
 *
 * LATCH: [evaluate] consumes its one shot on the FIRST call — whatever it
 * returns (including `None`), every later call in this process returns `None`.
 * That is the restore-loop guard: an `AutoRestore` navigation can never re-fire
 * the evaluation that caused it, and a re-composition (sign-out → sign-in) can
 * never re-offer a prompt the user already saw. The decision itself comes from
 * [decideSessionRestoration]; a snapshot fetch failure degrades to `null`
 * (→ `None`) and is logged — never thrown.
 *
 * The ask funnel: an `Ask` decision parks its snapshot in the controller
 * ([consumeAsk] takes it exactly once) so a dialog host that re-reads state on
 * recomposition cannot double-show or double-apply; [onRestoreHandled] clears
 * the parked ask (and is the shell's explicit "the restore reached the user"
 * ack on both arms).
 */
class SessionRestorationController(
    private val restoreMode: () -> SessionRestoreMode,
    private val fetchLatestSnapshot: suspend () -> SessionRestorationSnapshot?,
    private val readDismissal: suspend () -> String? = { null },
    private val persistDismissal: suspend (SessionRestorationSnapshot) -> Unit = {},
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {

    private val evaluated = AtomicBoolean(false)
    private val pendingAsk = AtomicReference<SessionRestorationSnapshot?>(null)

    /**
     * The process's one evaluation: fetch → decide → (Ask only) park the
     * snapshot. Returns `None` without fetching once the latch has fired.
     */
    suspend fun evaluate(launchContext: LaunchContext): SessionRestorationDecision {
        if (!evaluated.compareAndSet(false, true)) {
            Log.d(TAG, "session restore already decided this process; ignoring re-evaluation")
            return SessionRestorationDecision.None
        }
        val snapshot = try {
            fetchLatestSnapshot()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // A cancelled evaluation must not swallow cancellation — rethrow so
            // the caller's scope semantics survive (no decision was made, but
            // the composition that asked is going away anyway).
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "session-restore resume fetch failed; offering nothing", t)
            null
        }
        val decision = decideSessionRestoration(
            mode = restoreMode(),
            snapshot = snapshot,
            launchContext = launchContext,
            nowMs = nowMs(),
            dismissedFingerprint = readDismissal(),
        )
        if (decision is SessionRestorationDecision.Ask) {
            pendingAsk.set(decision.snapshot)
        }
        Log.d(
            TAG,
            "session restore decision for $launchContext: " +
                decision::class.simpleName +
                (snapshot?.let { " (item=${it.itemId})" } ?: " (no snapshot)"),
        )
        return decision
    }

    /**
     * Takes the parked `Ask` snapshot — `null` when there is none or it was
     * already consumed. The dialog host's idempotent re-read.
     */
    fun consumeAsk(): SessionRestorationSnapshot? = pendingAsk.getAndSet(null)

    /**
     * The shell's ack that the decision reached the user (prompt answered or
     * auto-restore applied): clears any parked ask. The [evaluated] latch is
     * NOT reset — the process gets exactly one decision, per the class KDoc.
     */
    fun onRestoreHandled() {
        pendingAsk.set(null)
    }

    /**
     * The prompt's dismiss arm: persists the offer's fingerprint through
     * [persistDismissal] so the next cold start's evaluation suppresses the
     * identical offer (rule 6 of [decideSessionRestoration]). The confirm arm
     * must NOT route here — a confirmed restore advances the server stamp on
     * its own; that cycle's next-launch silence is instead the TEARDOWN
     * marker's job (a deliberate player close persists the played-through
     * row state — see [SessionRestoreDismissal]).
     */
    suspend fun onAskDismissed(snapshot: SessionRestorationSnapshot) {
        persistDismissal(snapshot)
    }

    private companion object {
        private const val TAG = "SessionRestore"
    }
}
