package com.raulshma.jellyplay.core.ui.viewmodel

import androidx.compose.runtime.snapshots.Snapshot
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The one id-keyed enrichment choreography (the recorded per-site deferral,
 * landed over the three hand-copied fan-outs): "Semaphore-bounded per-id
 * fetch, skip already-present, atomic merge into the UI map, swallow per-item
 * failure". Call sites: Requests' media-details + *arr download-progress
 * enrichment (both on one shared semaphore), the Upcoming Calendar's poster
 * enrichment (its merge is the site's own append-only rule, now written
 * snapshot-atomically — the declared fix for its unsynchronized
 * read-then-write), and Next Up Exclusions' sequential hydration (concurrency
 * 1 + the [Retry.NEVER] failed-id latch).
 *
 * Division of labour: the enricher owns the WHEN (the bounded fan-out, the
 * per-item failure swallow, the snapshot-atomic merge application); the site
 * owns the WHAT — [fetch] maps one id to its payload (a `null` payload means
 * "nothing to merge", which is also how failures are expressed), [merge]
 * does the read-modify-write of the site's own state INSIDE the snapshot the
 * enricher opens (read and write both in the snapshot, so two completions
 * landing concurrently accumulate instead of losing one another's map
 * writes), and [enrich]'s `skip`/`onSettled` hooks carry the per-site
 * fan-out-time dedupe and per-id settle side effects.
 *
 * Failure swallow never masks cancellation: only non-[CancellationException]
 * throwers degrade to a skipped item. Under [Retry.NEXT_PASS] (the default) a
 * failed/skipped id may be retried by a later [enrich] call; under
 * [Retry.NEVER] it is latched in the failed set until it succeeds or
 * [pruneFailures] drops it — the no-retry-storm rule for screens whose
 * reconcile loop re-runs on every recomposition-driven emission.
 *
 * Concurrency discipline: [hasFailed]/[pruneFailures] and the internal
 * failed set are plain collections touched from the [scope]'s dispatcher
 * (the ViewModel main scope at every call site) and from inside the permit —
 * the same single-thread discipline the hand-copied originals ran under.
 *
 * [enrich] parents the per-id launches under one returned [Job] so a caller
 * whose pass can be superseded (Next Up Exclusions' reconcile) can cancel the
 * whole pass; call sites that fan out fire-and-forget ignore the return.
 */
class IdEnricher<Id, T>(
    private val scope: CoroutineScope,
    private val fetch: suspend (Id) -> T?,
    private val merge: (Id, T) -> Unit,
    /** Permit bound for the fan-out (the Semaphore(4) the originals shared). */
    private val concurrency: Int = DEFAULT_CONCURRENCY,
    private val retry: Retry = Retry.NEXT_PASS,
    /** Shareable permit pool — two site fan-outs can share one bound. */
    semaphore: Semaphore? = null,
) {

    /** Whether failed ids may be retried by a later [enrich] call. */
    enum class Retry { NEXT_PASS, NEVER }

    private val semaphore = semaphore ?: Semaphore(concurrency)
    private val failedIds = mutableSetOf<Id>()

    /**
     * Fans [ids] out: one permit-bounded fetch per id that [skip] (read at
     * fan-out time, against live state) and — under [Retry.NEVER] — the
     * failed latch don't already claim. Each completion folds through [merge]
     * inside one mutable snapshot; a settled id (merged OR swallowed) runs
     * [onSettled] inside its permit. Returns the pass [Job] (see class KDoc).
     */
    fun enrich(
        ids: List<Id>,
        skip: (Id) -> Boolean = { false },
        onSettled: (Id) -> Unit = {},
    ): Job = scope.launch {
        ids.forEach { id ->
            if (skip(id)) return@forEach
            if (retry == Retry.NEVER && id in failedIds) return@forEach
            launch {
                semaphore.withPermit {
                    val payload = try {
                        fetch(id)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Throwable) {
                        null
                    }
                    if (payload != null) {
                        Snapshot.withMutableSnapshot { merge(id, payload) }
                        failedIds.remove(id)
                    } else if (retry == Retry.NEVER) {
                        failedIds.add(id)
                    }
                    onSettled(id)
                }
            }
        }
    }

    /** Whether [id]'s fetch already failed this enricher's lifetime. */
    fun hasFailed(id: Id): Boolean = id in failedIds

    /** Drops latched failures for ids no longer relevant (the site's prune). */
    fun pruneFailures(ids: Collection<Id>) {
        failedIds.retainAll(ids.toSet())
    }

    private companion object {
        const val DEFAULT_CONCURRENCY = 4
    }
}
