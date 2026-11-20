package com.raulshma.jellyplay.core.data.worker

/**
 * The attempt-cap verdict of the retry ladder — THE one `attempt < max`
 * comparison behind every retrying worker/check: an attempt below the budget
 * retries, one at or past it is exhausted. The budgets themselves stay at
 * their owners (each worker's own constant, `AutoDownloadCheck.MAX_RETRIES`,
 * the notification worker's one-shot cap) — only the attempt-vs-cap mapping
 * folds here, so a cap change in one copy can no longer silently miss the
 * others. Pure common logic: the platform arms (WorkManager's
 * `Result.retry()`/`Result.failure()`, the desktop loop's give-up) are
 * applied by the callers.
 */
sealed interface WorkerRetryVerdict {

    /** [attempt] is still inside the budget — come back for another pass. */
    data object Retry : WorkerRetryVerdict {
        override val shouldRetry: Boolean get() = true
    }

    /** The budget is spent — give up (the caller's failure arm). */
    data object Exhausted : WorkerRetryVerdict {
        override val shouldRetry: Boolean get() = false
    }

    /** Compact spelling for boolean-mapped call sites. */
    val shouldRetry: Boolean

    companion object {

        /**
         * Folds one attempt against the cap. A non-positive [max] is a
         * disabled budget: every attempt (including the first) is exhausted.
         */
        fun of(attempt: Int, max: Int): WorkerRetryVerdict =
            if (attempt < max) Retry else Exhausted
    }
}
