package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.feature.book.epub.EpubBookCodec
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

/**
 * Pins the streamed book transfer source [EpubBookCodec]/EpubBookChunkSource:
 * fixed binary chunks (a multiple of three, so every chunk but possibly the
 * last base64-encodes standalone without padding), the running total header,
 * and the invariant that makes the per-chunk JS assembly equivalent to the
 * old whole-string decode — the chunks' base64 concatenates back to the
 * whole-file base64 exactly.
 */
class EpubBookCodecTest {

    private val tempDir = Files.createTempDirectory("book-codec-test")

    private fun bookFile(name: String, size: Int): java.nio.file.Path {
        val bytes = ByteArray(size) { (it * 31 + 7).toByte() }
        return Files.write(tempDir.resolve(name), bytes)
    }

    @Test
    fun `chunk bytes are a multiple of three at the transfer's char ceiling`() {
        assertEquals(0, EpubBookCodec.CHUNK_BYTES % 3, "every non-final chunk must base64-encode without padding")
        assertEquals(
            1_500_000,
            EpubBookCodec.CHUNK_BYTES / 3 * 4,
            "one chunk must land at exactly the per-evaluateJavascript base64 char ceiling",
        )
    }

    @Test
    fun `a small book is one chunk and reports its total bytes`() = runTest {
        val file = tempDir.resolve("small.epub")
        Files.write(file, "ABC".toByteArray())
        EpubBookCodec.openChunkedTransfer(file.toString().toPath()).use { source ->
            assertEquals(3L, source.totalBytes)
            assertEquals("QUJD", source.nextChunkBase64())
            assertNull(source.nextChunkBase64(), "exhausted source reads as end-of-book")
            assertNull(source.nextChunkBase64(), "staying exhausted stays end-of-book")
        }
    }

    @Test
    fun `chunks tile the file exactly and their base64 concatenates to the whole-file encoding`() = runTest {
        // 2.5 chunks: two full CHUNK_BYTES chunks + a short tail that is NOT a
        // multiple of three (the only padded segment, and it is the last).
        val size = EpubBookCodec.CHUNK_BYTES * 2 + 5
        val file = bookFile("multi.epub", size)
        val whole = Base64.getEncoder().encodeToString(Files.readAllBytes(file))
        val concatenated = StringBuilder()
        EpubBookCodec.openChunkedTransfer(file.toString().toPath()).use { source ->
            assertEquals(size.toLong(), source.totalBytes)
            var index = 0
            while (true) {
                val chunk = source.nextChunkBase64() ?: break
                if (index < 2) {
                    assertEquals(
                        EpubBookCodec.CHUNK_BYTES / 3 * 4,
                        chunk.length,
                        "chunk $index must encode padding-free at the full size",
                    )
                }
                concatenated.append(chunk)
                index++
            }
            assertEquals(3, index, "ceil(size / CHUNK_BYTES) chunks")
        }
        assertEquals(whole, concatenated.toString())
    }

    @Test
    fun `every non-final chunk is exactly chunk bytes and the last carries the remainder`() = runTest {
        val size = EpubBookCodec.CHUNK_BYTES + EpubBookCodec.CHUNK_BYTES / 2
        val file = bookFile("tail.epub", size)
        val decoded = ByteArrayOutputStream()
        EpubBookCodec.openChunkedTransfer(file.toString().toPath()).use { source ->
            val first = Base64.getDecoder().decode(source.nextChunkBase64())
            assertEquals(EpubBookCodec.CHUNK_BYTES, first.size)
            decoded.write(first)
            val second = Base64.getDecoder().decode(source.nextChunkBase64())
            assertEquals(size - EpubBookCodec.CHUNK_BYTES, second.size)
            decoded.write(second)
            assertNull(source.nextChunkBase64())
        }
        assertTrue(decoded.toByteArray().contentEquals(Files.readAllBytes(file)))
    }

    @Test
    fun `close is idempotent and closes the reads`() = runTest {
        val file = bookFile("close.epub", 3)
        val source = EpubBookCodec.openChunkedTransfer(file.toString().toPath())
        source.close()
        source.close()
        assertNull(source.nextChunkBase64(), "a closed source reads as end-of-book")
    }

    @Test
    fun `a missing book fails the open`() {
        val missing = tempDir.resolve("absent.epub")
        assertFailsWith<java.io.FileNotFoundException> {
            EpubBookCodec.openChunkedTransfer(missing.toString().toPath())
        }
    }
}
