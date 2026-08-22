package com.raulshma.jellyplay.core.data.worker

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until the OkHttp [Call] completes, yielding its [Response] — the
 * non-blocking equivalent of [Call.execute]. Used inside suspend workers so a
 * pending HTTP call doesn't tie up a worker thread. The call is
 * cancelled cooperatively when the suspending coroutine is cancelled.
 */
/**
 * C4 part 2 note: `internal` in the legacy `:core:data`; promoted to `public`
 * because the legacy `DownloadRepositoryImpl` still references it.
 */
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
