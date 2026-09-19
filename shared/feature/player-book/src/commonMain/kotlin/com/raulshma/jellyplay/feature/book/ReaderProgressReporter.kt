package com.raulshma.jellyplay.feature.book

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quiet period a page change / relocation waits before reporting position. */
private const val PROGRESS_DEBOUNCE_MS = 800L

/**
 * The report payload resolved from live session state: the encoded ticks for
 * the session's book kind and the exact-resume ride-along. [cfi] is non-null
 * only for a live reflowable session with a known anchor — the last-CFI
 * write is skipped for paged books and for reflowable sessions that never
 * relocated.
 */
internal data class ReaderProgressReport(
    val itemId: String,
    val ticks: Long,
    val cfi: String?,
)

/**
 * The reader's position-report choreography — the debounce, the final-flush
 * idempotence across onDispose + onCleared, the flush-scope escape and the
 * last-CFI ride-along previously inlined as BookReaderViewModel fields and
 * its `reportNow`/`scheduleProgressReport` pair (the ReaderControllers
 * constructor-lambda shape). Every live-value read goes through the injected
 * lambdas; the module holds no session state of its own beyond its latches.
 *
 * Invariants:
 * - the exit flush (`final = true`) is idempotent: the screen's onDispose AND
 *   the VM's onCleared both fire it on a normal exit, and the repository's
 *   final report also purges the item's caches, so the second call must not
 *   repeat the session-end choreography;
 * - [schedule] re-arms the final flush: reading activity after a
 *   config-change dispose means the next exit must flush again;
 * - the exit flush rides [flushScope] (process-wide): the VM scope is already
 *   cancelled by onCleared() before a plain launch there could run it;
 * - any reportNow call cancels the pending debounce FIRST, even when it then
 *   early-returns (no item, already flushed, no Ready session) — a stale
 *   debounce firing after an exit flush would report a second time.
 */
internal class ReaderProgressReporter(
    /** Mid-reading debounced reports launch here (cancelled with the VM). */
    private val scope: CoroutineScope,
    /** Process-wide scope for exit flushes that must survive onCleared. */
    private val flushScope: CoroutineScope,
    /** The loaded item; null before the first load (nothing to report). */
    private val itemId: () -> String?,
    /**
     * Resolves the report payload from live session state; null when there
     * is no Ready session — a failed load must not report position 0 and
     * wipe the server-side reading position on dispose.
     */
    private val buildReport: (itemId: String) -> ReaderProgressReport?,
    private val reportProgress: suspend (itemId: String, ticks: Long, final: Boolean) -> Unit,
    /** Persists the exact-resume anchor; only called with a non-null ride-along. */
    private val setLastCfi: (itemId: String, cfi: String) -> Unit,
) {

    private var finalReportFlushed = false
    private var debounceJob: Job? = null

    /**
     * Flush of the current position. `final = true` marks the exit flush
     * (dispose / back / onCleared) — the repository then also purges the
     * item's caches and announces the change. Debounced mid-reading reports
     * pass false: purging per page turn would thrash the caches.
     */
    fun reportNow(final: Boolean = true) {
        debounceJob?.cancel()
        debounceJob = null
        val item = itemId() ?: return
        if (final) {
            if (finalReportFlushed) return
            finalReportFlushed = true
        }
        val report = buildReport(item) ?: return
        (if (final) flushScope else scope).launch {
            runCatching {
                // The last-CFI write rides along so the exact-resume anchor
                // survives even when the reader closed within the debounce
                // window.
                if (report.cfi != null) setLastCfi(report.itemId, report.cfi)
                reportProgress(report.itemId, report.ticks, final)
            }
        }
    }

    /**
     * (Re-)arms the debounced mid-reading report. Cancels any pending one —
     * rapid page turns coalesce into the last — and resets the final-flush
     * latch: reading activity again (e.g. the screen re-created after the
     * config-change dispose already flushed) means the next exit must flush.
     */
    fun schedule() {
        finalReportFlushed = false
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(PROGRESS_DEBOUNCE_MS)
            reportNow(final = false)
        }
    }
}
