package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.model.FetchFailureCause
import com.raulshma.jellyplay.core.network.api.ApiException
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * The ONE classifier for why a detail fetch at the offline freshness seam
 * failed: both fold sites in [OfflineSyncManager] (the freshness check's and
 * `resyncItem`'s `getMediaDetail` Result) call it on the failure arm — a
 * second hand-fold of this mapping is a defect.
 *
 * The network layer classifies at throw time
 * (`ApiException.fromJellyfin`/`fromNetwork`: `isAccessDenied` for 401/403,
 * `httpCode` for an HTTP-status answer, the raw throwable preserved as
 * `cause`), so this walks the chain once re-expressing that classification as
 * the [FetchFailureCause] data the records carry. Null resolves to
 * [FetchFailureCause.OTHER] — both fold sites call it where `getOrNull()`
 * returned null, so the receiver is a failure payload in practice, but the
 * nullable receiver keeps the fold arms one-expression simple.
 *
 * Never classified: [kotlinx.coroutines.CancellationException] — the
 * repository wrapper (`runCatchingRethrowingCancellation`) rethrows
 * cancellation before it can land in a `Result.failure`, so this fold only
 * ever sees genuine fetch failures.
 */
internal fun Throwable?.fetchFailureCause(): FetchFailureCause {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_WALK_DEPTH) {
        when (current) {
            is ApiException -> {
                if (current.isAccessDenied) return FetchFailureCause.AUTH
                // Any other HTTP-status answer means the request round-tripped
                // and the server said no (404, 5xx, …).
                if (current.httpCode != null) return FetchFailureCause.SERVER
                // Otherwise keep walking: fromNetwork preserves the raw IO /
                // parse throwable as the cause.
            }
            is IOException -> return FetchFailureCause.NETWORK
            is SerializationException -> return FetchFailureCause.PARSE
        }
        current = current.cause
        depth++
    }
    return FetchFailureCause.OTHER
}

/** Cause chains are short; the cap only guards a pathological cycle. */
private const val MAX_CAUSE_WALK_DEPTH = 8
