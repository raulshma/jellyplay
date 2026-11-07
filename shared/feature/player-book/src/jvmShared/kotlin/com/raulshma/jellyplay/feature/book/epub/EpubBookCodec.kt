package com.raulshma.jellyplay.feature.book.epub

import java.io.File
import java.io.InputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path

/**
 * The book's boot transfer source. Both EPUB hosts are JVM (Android WebView /
 * desktop CEF), so this lives in jvmShared. The book travels base64-inlined
 * into the reader page — the WebView gets no file:// access — and it travels
 * in CHUNKS: the source reads [CHUNK_BYTES] binary bytes at a time and hands
 * each out as standalone base64, so no transfer step ever holds the whole
 * book's bytes or a whole-book base64 string (each chunk is dead once its
 * evaluateJavascript call completes).
 */
internal object EpubBookCodec {

    /**
     * Binary bytes per transfer chunk. A multiple of three, so every chunk
     * but possibly the last base64-encodes without padding and decodes
     * standalone (per-chunk `atob` on the JS side), and a padded final chunk
     * still terminates the concatenated stream correctly. 1_125_000 B maps
     * to exactly 1_500_000 base64 chars — the per-evaluateJavascript ceiling
     * the transfer always stayed under (multi-MB script strings fail on
     * Android WebView).
     */
    const val CHUNK_BYTES = 1_125_000

    /**
     * Opens [path] for one chunked transfer. Opening is a file read (the
     * constructor opens the stream eagerly, so a missing file fails here
     * instead of mid-transfer) — callers hop it off the main dispatcher.
     */
    fun openChunkedTransfer(path: Path): EpubBookChunkSource =
        EpubBookChunkSource(File(path.toString()))
}

/**
 * One opened book streaming out as fixed-size base64 chunks — the send
 * loop's only view of the file ([EpubBookTransfer], commonMain). Each chunk
 * except possibly the last is exactly [EpubBookCodec.CHUNK_BYTES] binary
 * bytes; the last carries the remainder. Concatenating the chunks' base64
 * reproduces the whole-file base64 exactly, and because every chunk decodes
 * standalone the JS side can write each decoded chunk straight into its
 * preallocated buffer at the running offset.
 *
 * Single-use, not thread-safe: one transfer at a time, sequential reads.
 * Reads (and the base64 encode) hop [Dispatchers.IO]/[Dispatchers.Default]
 * internally — the send loop itself runs on the caller's (main) dispatcher,
 * where evaluate(Java)Script must run. [close] is idempotent; a closed (or
 * exhausted) source reads as end-of-book.
 */
internal class EpubBookChunkSource(file: File) : EpubBookTransfer {

    /** Exact byte count the chunks reassemble to — the begin frame's header. */
    override val totalBytes: Long = file.length()

    private var input: InputStream? = file.inputStream()

    override suspend fun nextChunkBase64(): String? {
        val chunk = readNextChunk() ?: return null
        return withContext(Dispatchers.Default) { Base64.getEncoder().encodeToString(chunk) }
    }

    override fun close() {
        runCatching { input?.close() }
        input = null
    }

    /**
     * The next binary chunk, sized [EpubBookCodec.CHUNK_BYTES] until the
     * final short one (null after the last). The read loop fills fully —
     * a single `read` may return short for any reason.
     */
    private suspend fun readNextChunk(): ByteArray? = withContext(Dispatchers.IO) {
        val stream = input ?: return@withContext null
        val buffer = ByteArray(EpubBookCodec.CHUNK_BYTES)
        var off = 0
        while (off < buffer.size) {
            val read = stream.read(buffer, off, buffer.size - off)
            if (read < 0) break
            off += read
        }
        when {
            off == buffer.size -> buffer
            off > 0 -> buffer.copyOf(off)
            else -> {
                close()
                null
            }
        }
    }
}
