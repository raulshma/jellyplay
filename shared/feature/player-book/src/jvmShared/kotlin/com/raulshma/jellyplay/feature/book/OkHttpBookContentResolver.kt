package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.lastPathSegment
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * The streaming side of [BookContentResolver]: the download-vs-cache fork
 * rides the shared [PlaybackSourceResolver] on-disk predicate first, and only
 * a miss goes to the network. The cache root is ctor-injected (Android app
 * cacheDir / desktop data dir); the `reader-cache/<itemId>/` layout and the
 * filename sanitize live here so both platforms share one path scheme.
 */
class OkHttpBookContentResolver(
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val fetcher: BookHttpFetcher,
    private val cacheRoot: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : BookContentResolver {

    override suspend fun resolve(
        itemId: String,
        fileName: String?,
        format: BookFormat,
        downloadUrl: String,
        onProgress: (BookDownloadProgress) -> Unit,
    ): ResolvedBook {
        // 1. A completed on-disk download wins (the players' predicate — also
        //    re-checks that the file still exists, so vanished rows fall through).
        playbackSourceResolver.resolveUsableDownload(itemId)?.let { download ->
            return ResolvedBook.OfflineDownload(download.downloadPath.toPath())
        }

        // 2. The reader cache: <cacheRoot>/reader-cache/<itemId>/<sanitized-name>.
        val target = cacheRoot / CACHE_DIR / itemId / sanitizeFileName(fileName, format)
        if (fileSystem.exists(target)) return ResolvedBook.ReaderCache(target, downloadedNow = false)

        // cacheRoot/… always yields a parent; a null here would mean a
        // relative single-segment cacheRoot — falling through would return a
        // never-written path as a cache hit, so fail loudly instead.
        val parent = target.parent ?: error("Reader-cache target has no parent: $target")
        fileSystem.createDirectories(parent)
        // Fetch to a sibling .part and move atomically: resolve() treats any
        // existing file as a valid cache hit, so an interrupted direct write
        // would permanently poison the book with a truncated download.
        val part = parent / "${target.name}.part"
        try {
            fetcher.downloadToFile(downloadUrl, part, onProgress)
            fileSystem.atomicMove(part, target)
        } catch (t: Throwable) {
            runCatching { fileSystem.delete(part) }
            throw t
        }
        return ResolvedBook.ReaderCache(target, downloadedNow = true)
    }

    companion object {
        const val CACHE_DIR = "reader-cache"

        /**
         * Strip any path structure and filesystem-hostile characters; keep the
         * real extension so the opener's format dispatch stays honest. Blank
         * names fall back to the format's extension.
         */
        fun sanitizeFileName(fileName: String?, format: BookFormat): String {
            val base = fileName
                ?.lastPathSegment()
                ?.replace(ILLEGAL_FILE_CHARS, "_")
                ?.trim()
                .orEmpty()
            return base.ifBlank { "book.${format.extension}" }
        }

        private val ILLEGAL_FILE_CHARS = Regex("[\\\\/:*?\"<>|]")
    }
}

/** The network byte-mover seam — [OkHttpBookFetcher] is the only production impl; tests fake it. */
fun interface BookHttpFetcher {
    suspend fun downloadToFile(
        url: String,
        destination: Path,
        onProgress: (BookDownloadProgress) -> Unit,
    )
}

/** Plain streaming GET → file, with byte progress. Uses the ctor-injected (streaming) OkHttp client. */
class OkHttpBookFetcher(private val client: OkHttpClient) : BookHttpFetcher {

    override suspend fun downloadToFile(
        url: String,
        destination: Path,
        onProgress: (BookDownloadProgress) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val call = client.newCall(Request.Builder().url(url).build())
            // The call must not outlive the reader: cancellation (user backs
            // out mid-download) aborts the call — unblocking both the body
            // read loop and a stalled connect — and each chunk re-checks
            // cancellation so the loop exits promptly.
            call.executeCancellable().use { response ->
                check(response.isSuccessful) { "Book download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Book download failed: empty body")
                val total = body.contentLength().takeIf { it >= 0 }
                var downloaded = 0L
                FileSystem.SYSTEM.sink(destination).buffer().use { sink ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            downloaded += read
                            onProgress(BookDownloadProgress(downloaded, total))
                        }
                    }
                }
            }
        }
    }

    /**
     * Async [Call] as a cancellable suspend fun: cancelling the awaiting
     * coroutine cancels the OkHttp call, which aborts the connection so a
     * blocking body read can never outlive the reader.
     */
    private suspend fun Call.executeCancellable(): Response =
        suspendCancellableCoroutine { cont ->
            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
            cont.invokeOnCancellation { cancel() }
        }

    companion object {
        private const val DEFAULT_BUFFER_BYTES = 64 * 1024
    }
}
