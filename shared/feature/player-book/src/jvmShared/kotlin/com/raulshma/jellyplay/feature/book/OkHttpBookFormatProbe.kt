package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.parseContentDispositionFileName
import com.raulshma.jellyplay.core.network.auth.tokenAuthHeader
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * [BookFormatProbe] over the shared streaming OkHttp client: one ranged GET
 * (`Range: bytes=0-0`) against the book's download URL — HEAD is not
 * universally supported by servers/proxies, while a ranged GET costs one byte
 * and always carries the headers. The access token rides the
 * `Authorization: MediaBrowser` header (Jellyfin 12 401s the legacy
 * `?api_key=` query param on data endpoints); the URL keeps its capital
 * `ApiKey` param, which every server since 10.8 accepts. The body is closed
 * immediately; failures resolve to null (the reader falls back to its
 * path-based error naming).
 */
class OkHttpBookFormatProbe(
    private val client: OkHttpClient,
) : BookFormatProbe {

    override suspend fun probe(url: String, accessToken: String?): BookDownloadMetadata? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .apply {
                if (!accessToken.isNullOrBlank()) {
                    tokenAuthHeader(accessToken)
                }
            }
            .build()
        val call = client.newCall(request)
        try {
            call.await().use { response ->
                if (!response.isSuccessful) return@withContext null
                BookDownloadMetadata(
                    contentType = response.header("Content-Type"),
                    fileName = parseContentDispositionFileName(response.header("Content-Disposition")),
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            }
        })
        cont.invokeOnCancellation { cancel() }
    }
}
