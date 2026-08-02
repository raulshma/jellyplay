package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Named

/**
 * Narrow seam over the byte-transfer HTTP layer, decoupling the download
 * transfer loop ([DownloadTransferRunner]) from the concrete `OkHttpClient`.
 *
 * **Why this exists.** Before this seam the transfer loop built `okhttp3.Request`s
 * directly and called `Call.awaitResponse()`, welding the worker to OkHttp.
 * That made the hot path — the 250-line transfer method with its HTTP-status
 * branches (416/401/403/transient) and integrity checks — impossible to unit
 * test without a real server. This interface mirrors the shape of `FakeMediaEngine`:
 * a small production interface the runner depends on, so a test fake
 * ([FakeDownloadTransferClient] in `src/test`) can script responses (200/206/416/
 * 401/403/503, byte arrays, truncation, empty bodies, thrown `IOException`s)
 * and the policy branches become fast pure-JVM tests instead of Robolectric.
 *
 * The interface is deliberately close to what the loop consumes from an
 * `okhttp3.Response` — code, Content-Length, Content-Range, and the body
 * stream — so the production adapter is a thin wrapper and the fake has no
 * OkHttp types to satisfy. `HEAD` (content-size probe) and `GET` (transfer)
 * share one method: [execute] with a [TransferRequest.range] that is null for
 * a probe / fresh start and non-null for a resume.
 */
interface DownloadTransferClient {

    /**
     * Executes a single GET or HEAD against [request].
     *
     * @throws java.io.IOException on transport failure (timeout, reset, DNS).
     *   The runner routes these through [DownloadFailurePolicy.decide].
     */
    suspend fun execute(request: TransferRequest): TransferResponse
}

/**
 * One outbound transfer request. Built by the runner from a [DownloadEntity].
 *
 * @param url the download/stream URL.
 * @param head true for a content-size probe (no body consumed).
 * @param accessToken optional `X-Emby-Token`; null/blank omits the header.
 * @param range `bytes=N-` resume range, or null for a fresh/probe request.
 */
data class TransferRequest(
    val url: String,
    val head: Boolean = false,
    val accessToken: String? = null,
    val range: String? = null,
)

/**
 * The parts of an HTTP response the transfer loop consumes. Mirrors
 * `okhttp3.Response` minus everything the loop doesn't touch, so the fake
 * has no OkHttp dependency. A plain (non-sealed) interface so a test double
 * ([FakeDownloadTransferClient]'s response) can implement it from the test
 * source set.
 */
interface TransferResponse {

    val code: Int

    /**
     * The body's authoritative total size (bytes), or null if unknown.
     * - For 206: parsed from `Content-Range: bytes S-E/T` (the `/T` part).
     * - For 200: the `Content-Length` header.
     * - null when the header is absent or unparseable (chunked/transcoded).
     */
    val totalSize: Long?

    /**
     * Opens the response body as a byte stream. Only valid for 2xx responses
     * with a body (the runner only calls this on the happy path). The caller
     * must close it; the implementation closes the underlying response on
     * stream close.
     */
    fun openBody(): InputStream

    /** Release the response without consuming the body (used on non-2xx / after a read loop exits). */
    fun close()
}

/**
 * Production adapter over the pre-tuned `@Named("download")` [OkHttpClient]
 * (connect=30s, read=60s, write=30s). Thin: builds the Request, awaits
 * suspendingly, and projects the few headers the loop needs.
 */
class OkHttpDownloadTransferClient @Inject constructor(
    @Named("download") private val client: OkHttpClient,
) : DownloadTransferClient {

    override suspend fun execute(request: TransferRequest): TransferResponse =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(request.url)
                .header("User-Agent", "JellyPlay/1.0.0")
            if (request.head) builder.head()
            if (!request.accessToken.isNullOrBlank()) {
                builder.header("X-Emby-Token", request.accessToken)
            }
            if (!request.range.isNullOrBlank()) {
                builder.header("Range", request.range)
            }
            val response = client.newCall(builder.build()).awaitResponse()
            OkHttpResponse(response)
        }

    private class OkHttpResponse(private val response: okhttp3.Response) : TransferResponse {
        override val code: Int = response.code

        override val totalSize: Long? by lazy {
            if (code == 206) {
                // Content-Range: bytes S-E/T  →  T is the authoritative total.
                response.header("Content-Range")?.substringAfter("/")?.toLongOrNull()
            } else {
                response.body?.contentLength()?.takeIf { it > 0 }
            }
        }

        override fun openBody(): InputStream =
            response.body?.byteStream() ?: throw IOException("empty body")

        override fun close() = response.close()
    }
}
