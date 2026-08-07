package com.raulshma.jellyplay.feature.player.video.subtitle

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class SubtitlePreviewRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SubtitlePreviewRepository
    private var httpServer: HttpServer? = null

    private val srtBytes = """
        1
        00:00:01,000 --> 00:00:03,000
        Hello world

        2
        00:00:04,000 --> 00:00:06,000
        Second line
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private val vttBytes = """
        WEBVTT

        00:00:01.000 --> 00:00:03.000
        WebVTT line
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = SubtitlePreviewRepository(context, OkHttpClient())
    }

    @After
    fun tearDown() {
        httpServer?.stop(0)
    }

    @Test
    fun fileSource_parsesCuesViaCodecMapping() = runBlocking {
        val file = File.createTempFile("preview", ".srt").apply {
            writeBytes(srtBytes)
            deleteOnExit()
        }
        val source = subtitleSource(url = Uri.fromFile(file).toString(), codec = "srt")

        val cues = repository.loadCues(source)

        assertNotNull(cues)
        assertEquals(2, cues!!.size)
        assertEquals("Hello world", cues[0].text.toString())
    }

    @Test
    fun vttCodec_mapsToTextVttAndParses() = runBlocking {
        val file = File.createTempFile("preview", ".vtt").apply {
            writeBytes(vttBytes)
            deleteOnExit()
        }
        val source = subtitleSource(url = Uri.fromFile(file).toString(), codec = "vtt")

        val cues = repository.loadCues(source)

        assertNotNull(cues)
        assertEquals(1, cues!!.size)
        assertEquals("WebVTT line", cues[0].text.toString())
    }

    @Test
    fun explicitMimeType_takesPrecedenceOverCodec() = runBlocking {
        val file = File.createTempFile("preview", ".srt").apply {
            writeBytes(vttBytes)
            deleteOnExit()
        }
        // codec says srt but the explicit mime (text/vtt) wins — and parses the VTT bytes.
        val source = subtitleSource(url = Uri.fromFile(file).toString(), codec = "srt", mimeType = "text/vtt")

        val cues = repository.loadCues(source)

        assertNotNull(cues)
        assertEquals("WebVTT line", cues!!.first().text.toString())
    }

    @Test
    fun unsupportedCodec_returnsNull() = runBlocking {
        // ASS/SSA is not parseable by DefaultSubtitleParserFactory in the
        // headless preview context; the repository must signal "unavailable".
        val source = subtitleSource(url = "file:///nonexistent.ass", codec = "ass")

        assertNull(repository.loadCues(source))
    }

    @Test
    fun embeddedSource_withoutUrl_returnsNull() = runBlocking {
        // DIRECT_PLAY embedded tracks (image subs like PGS, or unknown formats)
        // never carry a fetchable URL — the preview must stay null, never a
        // guess at some other track's bytes.
        val source = SubtitleSource(
            id = "embedded:pgs",
            url = "",
            label = "PGS",
            language = "en",
            mimeType = null,
            codec = null,
            isDefault = false,
            isForced = true,
        )

        assertNull(repository.loadCues(source))
    }

    @Test
    fun unreadableFile_returnsNull() = runBlocking {
        val source = subtitleSource(url = "file:///nonexistent/never-there.srt", codec = "srt")

        assertNull(repository.loadCues(source))
    }

    @Test
    fun unparseableBytes_returnsNull() = runBlocking {
        val file = File.createTempFile("preview", ".srt").apply {
            writeBytes("not a subtitle file at all".toByteArray())
            deleteOnExit()
        }
        val source = subtitleSource(url = Uri.fromFile(file).toString(), codec = "srt")

        assertNull(repository.loadCues(source))
    }

    @Test
    fun contentUri_readsViaContentResolver() = runBlocking {
        val contentUri = Uri.parse("content://preview.authority/subs/test.srt")
        shadowOf(context.contentResolver).registerInputStream(contentUri, java.io.ByteArrayInputStream(srtBytes))
        val source = subtitleSource(url = contentUri.toString(), codec = "srt")

        val cues = repository.loadCues(source)

        assertNotNull(cues)
        assertEquals(2, cues!!.size)
    }

    @Test
    fun httpSource_sendsAuthHeadersAndParses() = runBlocking {
        val receivedHeader = AtomicReference<String?>(null)
        httpServer = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/subs") { exchange ->
                receivedHeader.set(exchange.requestHeaders.getFirst("X-Emby-Token"))
                exchange.sendResponseHeaders(200, srtBytes.size.toLong())
                exchange.responseBody.use { it.write(srtBytes) }
            }
            start()
        }
        val url = "http://127.0.0.1:${httpServer!!.address.port}/subs/en.srt"
        val source = subtitleSource(url = url, codec = "srt")

        val cues = repository.loadCues(source, headers = mapOf("X-Emby-Token" to "test-token"))

        assertNotNull(cues)
        assertEquals(2, cues!!.size)
        assertEquals("test-token", receivedHeader.get())
    }

    @Test
    fun httpFailure_returnsNull() = runBlocking {
        val source = subtitleSource(url = "http://127.0.0.1:1/sub/srt", codec = "srt")

        assertNull(repository.loadCues(source))
    }

    @Test
    fun cues_memoizedPerUrl_untilCacheCleared() = runBlocking {
        val file = File.createTempFile("preview", ".srt").apply {
            writeBytes(srtBytes)
            deleteOnExit()
        }
        val url = Uri.fromFile(file).toString()
        val source = subtitleSource(url = url, codec = "srt")

        val first = repository.loadCues(source)
        file.writeBytes("1\n00:00:09,000 --> 00:00:10,000\nChanged line\n".toByteArray())

        // Cache hit: still the original cues despite the file changing.
        val cached = repository.loadCues(source)
        assertEquals(first!!.size, cached!!.size)
        assertEquals("Hello world", cached[0].text.toString())

        // Explicit clear forces a re-read of the new content.
        repository.clearCache(url)
        val reloaded = repository.loadCues(source)
        assertNotNull(reloaded)
        assertEquals("Changed line", reloaded!![0].text.toString())
    }

    @Test
    fun clearCache_withoutUrl_clearsAllSources() = runBlocking {
        val file = File.createTempFile("preview", ".srt").apply {
            writeBytes(srtBytes)
            deleteOnExit()
        }
        val url = Uri.fromFile(file).toString()
        val source = subtitleSource(url = url, codec = "srt")
        assertNotNull(repository.loadCues(source))

        repository.clearCache()

        file.writeBytes("1\n00:00:09,000 --> 00:00:10,000\nChanged line\n".toByteArray())
        val reloaded = repository.loadCues(source)
        assertEquals("Changed line", reloaded!![0].text.toString())
    }

    private fun subtitleSource(
        url: String,
        codec: String?,
        mimeType: String? = null,
    ) = SubtitleSource(
        url = url,
        label = "English",
        language = "en",
        mimeType = mimeType,
        codec = codec,
        isDefault = true,
        isForced = false,
        id = "test:$url",
    )
}
