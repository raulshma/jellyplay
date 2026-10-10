package com.raulshma.jellyplay.core.concurrency

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Temporal policy for fetch-shaped work — the three questions every poll /
 * deadline / serialize site in this repo used to answer with hand-rolled
 * copies (EpgViewModel, AdminApiClientImpl, ServerDiscoveryService,
 * HomeRefresher, ServerHealthMonitor, SyncPlayManager; five recent production
 * fixes landed inside those copies), gathered under one vocabulary:
 *
 *  - **Deadline — how long** ([withDeadlineMs]): one hard outer envelope,
 *    because only OkHttp's per-call read timeout exists below it and a
 *    half-open socket defeats even that.
 *  - **Single-flight — who** ([SingleFlight]): keyed mutual exclusion, so two
 *    triggers of the same fetch serialize instead of duplicating.
 *  - **Cadence — how often** ([boundedPoll]): rounds separated by real gaps,
 *    so a busy server is waited out, not hammered gap-free.
 *
 * Per-attempt RETRY — which failures to re-attempt and how many times — stays
 * in core:network's `RetryPolicy`, where it already lives. The two are
 * orthogonal and compose in exactly one direction: an envelope or a cadence
 * wraps a retrying call, never inside it (the retry must see the deadline's
 * cancellation to give up; a deadline that fired per retry attempt would
 * multiply rather than bound the wait).
 *
 * Nothing here touches an HTTP client, a repository or a dispatcher —
 * kotlinx-coroutines primitives only, common code.
 */

/**
 * Bounded fetch envelope: runs [block] under [withTimeoutOrNull] semantics —
 * the value if it finishes inside [deadlineMs], `null` once the envelope
 * expires (the block is cancelled at its next suspension point). Thin on
 * purpose; the vocabulary is the point: a fetch site that returns
 * null-on-expiry reads as declared policy, not as an unexamined wrapper call.
 *
 * Exists for the failure mode [HomeRefresher] documented when it grew its 30s
 * cap and [ServerDiscoveryService] when it bounded its SDK scan window
 * (28e954187): below this envelope there is ONLY OkHttp's per-call read
 * timeout — which a half-open socket (bytes never arrive, connection never
 * errors) or a hung server-side await defeats, parking the caller forever.
 * HomeRefresher's stuck-fetch hung on its refresh mutex with the loader never
 * clearing until app restart. One envelope at the fetch boundary caps the
 * whole wait; per-request read timeouts stay what they are.
 *
 * A caller's own [kotlin.coroutines.cancellation.CancellationException]
 * propagates untouched — a cancelled caller must stay cancelled; only the
 * deadline's own timeout degrades to `null` ([withTimeoutOrNull] consumes
 * exactly that one).
 */
suspend fun <T> withDeadlineMs(deadlineMs: Long, block: suspend () -> T): T? =
    withTimeoutOrNull(deadlineMs) { block() }

/**
 * Keyed mutual exclusion for fetch-style critical sections — the per-key
 * `Mutex` every multi-trigger fetch site hand-rolls, without the trap that
 * shipped the EPG crash (99e665a35): a bare `Mutex()` field reached by a
 * coroutine launched from an init block on Main.immediate runs before the
 * field's declaration line has executed ("Mutex.lock on a null object
 * reference" when opening the Guide tabs). Here the lock table is born —
 * empty, complete — inside the [SingleFlight] val itself; a launched-in-init
 * coroutine can never observe an uninitialized lock, because there is no
 * per-key lock declaration left to race the constructor.
 *
 * [inFlight] semantics, declared:
 *
 *  - **Same key serializes.** Callers queue on the key's mutex and each
 *    eventually runs — no coalescing, no supersession, no newest-wins: a
 *    queued pass runs to completion even if newer triggers arrived while it
 *    waited (the trailing-refresh contract EpgViewModel pinned when it
 *    serialized guide fetches). If the policy you want is "run only if not
 *    already running", that is a DIFFERENT tool and it is deliberately
 *    absent here (no consumer; ConnectionProbe's arms own the restart
 *    flavor where it exists).
 *  - **Different keys never block each other.** One mutex per key, created
 *    atomically on first use.
 *  - **NOT reentrant.** Re-entering the same key from inside the block
 *    deadlocks the owning coroutine — kotlinx Mutex has no reentrancy; the
 *    block must not recurse into its own key.
 *  - **Entries live forever.** No eviction: the contract is a BOUNDED key
 *    space (item ids, server urls, section names), never an unbounded one
 *    (per-frame, per-timestamp keys would leak one mutex per key).
 *
 * Thread-safe: the table is a [ConcurrentHashMap] — imported directly, the
 * module's JVM-shaped-targets precedent (all targets compile commonMain as
 * JVM bytecode; [RestartableJob] already carries JVM APIs in commonMain).
 * Lookup-or-create is atomic per key; the mutex handoff does the rest.
 *
 * NOT the namesake of core:data jvmShared's `SingleFlight<K, V>` — that one
 * is a keyed getOrFetch MEMO (first caller computes, later callers await its
 * Deferred); this one is a keyed MUTEX (every caller runs, serialized).
 */
class SingleFlight {

    private val locks = ConcurrentHashMap<Any, Mutex>()

    /**
     * Runs [block] under [key]'s mutex: same-key callers queue and run one
     * at a time, different keys run concurrently. The block's result,
     * exception or cancellation propagates unchanged — and the key is
     * released either way, so the next caller is never parked behind a
     * failed or cancelled pass.
     */
    suspend fun <T> inFlight(key: Any, block: suspend () -> T): T =
        locks.computeIfAbsent(key) { Mutex() }.withLock { block() }
}

/**
 * The whole-poll cadence spec for [boundedPoll] — one value so a call site
 * reads as a policy (`PollSpec(intervalMs = 250, maxAttempts = 10)`) instead
 * of a six-positional-argument prayer.
 */
data class PollSpec(
    /**
     * The gap between rounds, honored AFTER each null attempt: attempt N+1
     * never fires before [intervalMs] after attempt N.
     */
    val intervalMs: Long,
    /**
     * Round cap — an attempt that runs and returns null consumes the attempt
     * (the busy-server rule: "busy, try later" is a completed round, not a
     * free retry). Default unbounded.
     */
    val maxAttempts: Int = Int.MAX_VALUE,
    /**
     * Whole-poll deadline under [withTimeoutOrNull] semantics — null (the
     * default) runs unbounded, a value yields a `null` result on expiry,
     * indistinguishable from an exhausted poll.
     */
    val envelopeMs: Long? = null,
    /**
     * Multiplicative interval growth per null round; 1.0 (the default) keeps
     * a fixed cadence.
     */
    val backoffFactor: Double = 1.0,
    /**
     * Backoff cap. Defaults to [intervalMs], which with the default factor
     * means fixed cadence — raise it (with a factor > 1.0) to actually grow
     * the gap.
     */
    val maxIntervalMs: Long = intervalMs,
    /**
     * Uniform `[0, jitterMs)` added per gap (default 0) so a fleet of
     * pollers does not sync into a beat.
     */
    val jitterMs: Long = 0,
    /**
     * Jitter source, defaulting to [Random.Default] — a test seam, not a
     * policy knob; inject a seeded [Random] to pin jitter in tests.
     */
    val random: Random = Random.Default,
)

/**
 * Poll with a cadence policy: calls [attempt] in rounds numbered from 1 and
 * returns the first non-null answer; `null` means the poll ended without
 * one. Deletes the gap-free poll bug — AdminApiClientImpl's createBackup
 * list-poll burned its remaining attempts back-to-back when a busy server
 * answered null (43c806a90) — and gives every future scan a whole-operation
 * window instead of ServerDiscoveryService's hand-bounded one (28e954187).
 *
 * Declared policies:
 *
 *  - **A null attempt consumes the round** (the busy-server rule): the
 *    [PollSpec.intervalMs] gap is honored after it, grown per
 *    [PollSpec.backoffFactor] and capped at [PollSpec.maxIntervalMs], widened
 *    by up to [PollSpec.jitterMs] of uniform jitter.
 *  - **[PollSpec.maxAttempts] caps the rounds**; the last null attempt also
 *    consumes its round — the poll returns `null` immediately after it, with
 *    NO trailing delay.
 *  - **[PollSpec.envelopeMs] caps the whole poll** ([withTimeoutOrNull]
 *    semantics): expiry mid-attempt or mid-gap cancels whatever is running
 *    and returns `null`.
 *  - **A non-null answer returns immediately**, mid-round, delay discarded.
 *  - [kotlin.coroutines.cancellation.CancellationException] propagates from
 *    inside [attempt] and from inside the gap — including through a
 *    [PollSpec.envelopeMs] envelope, which only ever consumes its own
 *    timeout.
 *
 * Retry is NOT owned here: which failures to re-attempt stays in
 * core:network's `RetryPolicy` — a cadence wraps a retrying call, never the
 * reverse (see the file KDoc).
 */
suspend fun <T> boundedPoll(
    poll: PollSpec,
    attempt: suspend (round: Int) -> T?,
): T? {
    if (poll.envelopeMs == null) return pollRounds(poll, attempt)
    return withTimeoutOrNull(poll.envelopeMs) { pollRounds(poll, attempt) }
}

private suspend fun <T> pollRounds(
    poll: PollSpec,
    attempt: suspend (round: Int) -> T?,
): T? {
    var round = 1
    while (true) {
        val answer = attempt(round)
        if (answer != null) return answer
        // The null answer consumed the round (busy-server rule): the last
        // attempt gets no trailing delay, every other one is followed by the
        // grown-capped-jittered gap.
        if (round >= poll.maxAttempts) return null
        val grown = (poll.intervalMs * poll.backoffFactor.pow(round - 1)).toLong()
        val gapMs = grown.coerceAtMost(poll.maxIntervalMs)
        delay(gapMs + poll.nextJitterMs())
        round++
    }
}

private fun PollSpec.nextJitterMs(): Long =
    if (jitterMs > 0) random.nextLong(jitterMs) else 0L
