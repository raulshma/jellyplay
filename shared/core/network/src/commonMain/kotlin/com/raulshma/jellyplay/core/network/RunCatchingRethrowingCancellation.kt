package com.raulshma.jellyplay.core.network

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] variant for code that runs inside (or alongside) a cancellable
 * coroutine: a [CancellationException] must pass through. Plain inline
 * `runCatching` around suspend calls catches it like any other [Throwable] —
 * swallowing structured cancellation (a cancelled prewarm reports as a failed
 * result instead of actually stopping) and masking
 * [kotlinx.coroutines.withTimeoutOrNull] timeouts (the timed-out stage reports
 * success, so any deferred re-run is skipped).
 *
 * Inline so callers keep `runCatching`'s ergonomics — non-local `return` /
 * `return@runCatchingRethrowingCancellation` from the block works exactly as
 * it does with the stdlib helper.
 */
suspend inline fun <T> runCatchingRethrowingCancellation(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
