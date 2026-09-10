package com.raulshma.jellyplay.core.concurrency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Named, cancel-and-replace job slots over a caller-owned [CoroutineScope] —
 * the "cancel the old one when the new one arrives" choreography that player
 * engines, health monitors and trickplay preloads each hand-rolled:
 *
 * ```
 * private var preloadJob: Job? = null          // becomes: private val tasks = TaskBundle(scope)
 * preloadJob?.cancel()                         //            tasks.replace(KEY) { launch { ... } }
 * preloadJob = scope.launch { ... }
 * ```
 *
 * The bundle owns ONLY the slot choreography — never the scope. Scope
 * lifecycle (recreation on reset, cancellation on dispose) stays at the site
 * that tangles it with its own state eviction; dispatchers and supervision
 * are whatever the caller's scope declares. [Job]s launched outside
 * [replace] (straight `scope.launch`) are untracked, exactly as before.
 *
 * NOT thread-safe by design: callers confine access to the scope's dispatcher
 * — the same contract the plain-var `Job?` fields it replaces already ran
 * under (none of them were `@Volatile`). For keyed tasks that must be
 * cancelled from a different thread, cancel the scope, not the bundle.
 */
class TaskBundle(private val scope: CoroutineScope) {

    private val tasks = mutableMapOf<Any, Job>()

    /**
     * Cancels whatever occupies [key] and launches [block] into the scope,
     * tracking the returned [Job] under the key. Cancel-then-launch, in that
     * order — same ordering as the hand-rolled `job?.cancel(); job = launch {}`
     * pairs this replaces (the cancelled job stops cooperatively; the
     * replacement does not wait for it).
     */
    fun replace(key: Any, block: CoroutineScope.() -> Job): Job {
        tasks.remove(key)?.cancel()
        return block(scope).also { tasks[key] = it }
    }

    /** Cancels the task at [key], if any, and forgets it. */
    fun cancel(key: Any) {
        tasks.remove(key)?.cancel()
    }

    /** Forgets the task at [key] WITHOUT cancelling it (a hand-off). */
    fun forget(key: Any) {
        tasks.remove(key)
    }

    /** Cancels every tracked task and empties the bundle. The scope lives on. */
    fun cancelAll() {
        tasks.values.forEach { it.cancel() }
        tasks.clear()
    }

    /** The live job at [key], if any — for join-on-release sites. */
    operator fun get(key: Any): Job? = tasks[key]
}
