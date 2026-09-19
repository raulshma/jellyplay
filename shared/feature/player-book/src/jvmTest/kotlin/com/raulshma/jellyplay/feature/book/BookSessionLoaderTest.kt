package com.raulshma.jellyplay.feature.book

import androidx.compose.ui.graphics.ImageBitmap
import com.raulshma.jellyplay.core.data.repository.BookTocCache
import com.raulshma.jellyplay.core.data.repository.BookTocCacheRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.BookTocEntry
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath

/**
 * Pins [BookSessionLoader] — the open sequence extracted from
 * BookReaderViewModel's `openBook`: the error taxonomy (cannot-open vs
 * unsupported-format with its dotted-extension derivation), the
 * download-header probe fallback, the reflowable/paged resume math, the
 * hook positions (reset before the detail fetch, format latch after the
 * resolve, download progress forwarding) and the PDF-vs-comic TOC-cache
 * branch. Collaborators are value fakes plus mockk stubs (module jvmTest
 * conventions); the outline-parse hop rides the test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookSessionLoaderTest {

    private val mediaRepository = mockk<MediaRepository>()
    private val playbackRepository = mockk<PlaybackRepository>()
    private val contentResolver = FakeContentResolver()
    private val documentOpener = FakeDocumentOpener(pageCount = 3)
    private val pdfOutlineParser = mockk<PdfOutlineParser>()
    private val tocCache = RecordingTocCache()
    private var probeMetadata: BookDownloadMetadata? = null
    private val probeCalls = mutableListOf<Pair<String, String?>>()

    private fun detail(path: String?, positionTicks: Long = 0L): MediaDetail = MediaDetail(
        item = MediaItem(id = "item-1", name = "A Book", mediaType = MediaType.BOOK),
        path = path,
        playbackPositionTicks = positionTicks,
    )

    private fun stubDetail(path: String?, positionTicks: Long = 0L) {
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(detail(path, positionTicks))
    }

    private fun TestScope.loader(
        storedResumeCfi: (String) -> String? = { null },
        onSessionReset: () -> Unit = {},
        onDownloadProgress: (Float?) -> Unit = {},
        onFormatResolved: (BookFormat) -> Unit = {},
        parseDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): BookSessionLoader = BookSessionLoader(
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        contentResolver = contentResolver,
        documentOpener = documentOpener,
        formatProbe = { url, token ->
            probeCalls += url to token
            probeMetadata
        },
        pdfOutlineParser = pdfOutlineParser,
        tocCacheRepository = tocCache,
        storedResumeCfi = storedResumeCfi,
        onSessionReset = onSessionReset,
        onDownloadProgress = onDownloadProgress,
        onFormatResolved = onFormatResolved,
        parseDispatcher = parseDispatcher,
    )

    private fun stubPlayback() {
        every { playbackRepository.getBookDownloadUrl("item-1") } returns "https://server/download"
        every { playbackRepository.getAccessToken() } returns "token"
    }

    // ------------------------------------------------------------------
    // Error classification
    // ------------------------------------------------------------------

    @Test
    fun `known but unreadable path extension classifies unsupported with a dotted extension`() = runTest {
        stubDetail("/books/novel.mobi")
        stubPlayback()
        probeMetadata = null // the probe ran and learned nothing

        val outcome = loader().open("item-1", jump = null)

        val failed = assertIs<BookSessionOutcome.Failed>(outcome)
        val error = assertIs<BookOpenError.UnsupportedFormat>(failed.error)
        assertEquals(".mobi", error.fileExtension)
        // The probe saw the download URL and the bearer token.
        assertEquals(listOf<Pair<String, String?>>("https://server/download" to "token"), probeCalls)
    }

    @Test
    fun `blank path with a failed probe classifies unsupported without an extension`() = runTest {
        stubDetail(path = null)
        stubPlayback()
        probeMetadata = null

        val outcome = loader().open("item-1", jump = null)

        val failed = assertIs<BookSessionOutcome.Failed>(outcome)
        val error = assertIs<BookOpenError.UnsupportedFormat>(failed.error)
        assertNull(error.fileExtension)
    }

    @Test
    fun `detail fetch failure classifies cannot open`() = runTest {
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.failure(IllegalStateException("offline"))
        stubPlayback()

        val outcome = loader().open("item-1", jump = null)

        val failed = assertIs<BookSessionOutcome.Failed>(outcome)
        assertEquals(BookOpenError.CannotOpen, failed.error)
    }

    @Test
    fun `content resolve failure classifies cannot open`() = runTest {
        stubDetail("comic.cbz")
        stubPlayback()
        contentResolver.fail = true

        val outcome = loader().open("item-1", jump = null)

        assertEquals(BookOpenError.CannotOpen, assertIs<BookSessionOutcome.Failed>(outcome).error)
    }

    @Test
    fun `document open failure classifies cannot open`() = runTest {
        stubDetail("comic.cbz")
        stubPlayback()
        documentOpener.fail = true

        val outcome = loader().open("item-1", jump = null)

        assertEquals(BookOpenError.CannotOpen, assertIs<BookSessionOutcome.Failed>(outcome).error)
    }

    @Test
    fun `unsupportedFormatFileExtension derives the dotted lowercase extension purely`() {
        assertEquals(".mobi", unsupportedFormatFileExtension("/books/novel.MOBI"))
        assertEquals(".cbz", unsupportedFormatFileExtension("https://s/book.cbz?api_key=x"))
        assertNull(unsupportedFormatFileExtension(null))
        assertNull(unsupportedFormatFileExtension("  "))
        assertNull(unsupportedFormatFileExtension("no-extension"))
    }

    // ------------------------------------------------------------------
    // Format probe fallback + reflowable outcome
    // ------------------------------------------------------------------

    @Test
    fun `probe fallback resolves the format from download headers`() = runTest {
        stubDetail(path = null)
        stubPlayback()
        probeMetadata = BookDownloadMetadata(contentType = "application/pdf", fileName = null)

        val outcome = loader().open("item-1", jump = null)

        val paged = assertIs<BookSessionOutcome.Paged>(outcome)
        assertEquals(BookFormat.PDF, paged.format)
    }

    @Test
    fun `reflowable outcome carries resume percent stored cfi and jump href`() = runTest {
        stubDetail("novel.epub", positionTicks = BookProgressPolicy.percentToTicks(0.3))
        stubPlayback()

        val outcome = loader(storedResumeCfi = { "epubcfi(/6/12!/4/2)" })
            .open("item-1", jump = PendingJump(href = "ch1.xhtml", page = null))

        val reflowable = assertIs<BookSessionOutcome.Reflowable>(outcome)
        assertEquals("A Book", reflowable.title)
        assertEquals("cache/book".toPath(), reflowable.bookFile)
        assertEquals(0.3, reflowable.resumePercent)
        assertEquals("epubcfi(/6/12!/4/2)", reflowable.resumeCfi)
        assertEquals("ch1.xhtml", reflowable.jumpHref)
        // Reflowable books never enter the document layer.
        assertEquals(0, documentOpener.opens)
    }

    // ------------------------------------------------------------------
    // Paged resume math
    // ------------------------------------------------------------------

    @Test
    fun `paged deep link page outranks the server resume position`() = runTest {
        stubDetail("comic.cbz", positionTicks = BookProgressPolicy.pageToTicks(1))
        stubPlayback()

        val outcome = loader().open("item-1", jump = PendingJump(href = null, page = 2))

        assertEquals(2, assertIs<BookSessionOutcome.Paged>(outcome).resumePage)
    }

    @Test
    fun `paged resume clamps the server ticks to the document page count`() = runTest {
        stubDetail("comic.cbz", positionTicks = BookProgressPolicy.pageToTicks(9))
        stubPlayback()

        val outcome = loader().open("item-1", jump = null)
        val paged = assertIs<BookSessionOutcome.Paged>(outcome)

        assertEquals(2, paged.resumePage)
        // The handed-over document is already re-anchored at the resume page.
        assertEquals(listOf(2), documentOpener.anchoredPages)
        assertEquals(3, paged.document.pageCount)
    }

    // ------------------------------------------------------------------
    // Hook positions
    // ------------------------------------------------------------------

    @Test
    fun `session reset runs before the detail fetch`() = runTest {
        val order = mutableListOf<String>()
        coEvery { mediaRepository.getMediaDetail("item-1") } answers {
            order += "detail"
            Result.success(detail("comic.cbz"))
        }
        stubPlayback()

        loader(onSessionReset = { order += "reset" }).open("item-1", jump = null)

        assertEquals(listOf("reset", "detail"), order)
    }

    @Test
    fun `download progress hook forwards the streaming fraction`() = runTest {
        stubDetail("comic.cbz")
        stubPlayback()
        val fractions = mutableListOf<Float?>()

        loader(onDownloadProgress = { fractions += it }).open("item-1", jump = null)

        assertEquals(listOf<Float?>(0.5f), fractions)
    }

    @Test
    fun `format latch hook fires after a successful resolve even when the document fails to open`() = runTest {
        stubDetail("comic.cbz")
        stubPlayback()
        documentOpener.fail = true
        val formats = mutableListOf<BookFormat>()

        loader(onFormatResolved = { formats += it }).open("item-1", jump = null)

        assertEquals(listOf(BookFormat.CBZ), formats)
    }

    // ------------------------------------------------------------------
    // TOC cache branch
    // ------------------------------------------------------------------

    @Test
    fun `comic open caches the page count without parsing an outline`() = runTest {
        stubDetail("comic.cbz")
        stubPlayback()

        val paged = assertIs<BookSessionOutcome.Paged>(loader().open("item-1", jump = null))
        loader().startOpenTocCache("item-1", paged.format, paged.file, paged.document.pageCount) {}
        advanceUntilIdle()

        assertEquals(
            listOf(RecordingTocCache.Put("item-1", BookFormat.CBZ, pageCount = 3, entries = emptyList())),
            tocCache.puts,
        )
        verify(exactly = 0) { pdfOutlineParser.parse(any()) }
    }

    @Test
    fun `pdf open parses the outline reports it and flattens it into the cache`() = runTest {
        stubDetail("doc.pdf")
        stubPlayback()
        every { pdfOutlineParser.parse(any()) } returns listOf(
            PdfOutlineNode(
                title = "Chapter 1",
                pageIndex = 0,
                children = listOf(PdfOutlineNode(title = "Section 1.1", pageIndex = 1, children = emptyList())),
            ),
        )
        val reported = mutableListOf<List<PdfOutlineNode>>()

        val paged = assertIs<BookSessionOutcome.Paged>(loader().open("item-1", jump = null))
        loader().startOpenTocCache("item-1", paged.format, paged.file, paged.document.pageCount) { nodes ->
            reported += nodes
        }
        advanceUntilIdle()

        assertEquals(1, reported.size)
        assertEquals("Chapter 1", reported.single().single().title)
        assertEquals(
            listOf(
                BookTocEntry(label = "Chapter 1", href = null, page = 0, level = 0),
                BookTocEntry(label = "Section 1.1", href = null, page = 1, level = 1),
            ),
            tocCache.puts.single().entries,
        )
        assertEquals(BookFormat.PDF, tocCache.puts.single().format)
        assertEquals(3, tocCache.puts.single().pageCount)
    }

    @Test
    fun `pdf open with no outline reports the empty tree and skips the cache write`() = runTest {
        stubDetail("doc.pdf")
        stubPlayback()
        every { pdfOutlineParser.parse(any()) } returns emptyList()
        val reported = mutableListOf<List<PdfOutlineNode>>()

        val paged = assertIs<BookSessionOutcome.Paged>(loader().open("item-1", jump = null))
        loader().startOpenTocCache("item-1", paged.format, paged.file, paged.document.pageCount) { nodes ->
            reported += nodes
        }
        advanceUntilIdle()

        assertEquals(listOf(emptyList<PdfOutlineNode>()), reported)
        assertTrue(tocCache.puts.isEmpty(), "an empty outline must not clobber the cache")
    }

    @Test
    fun `host toc forward writes entries without a page count only when it has rows`() = runTest {
        val l = loader()
        l.cacheToc("item-1", BookFormat.EPUB, pageCount = 0, entries = listOf(BookTocEntry("Ch 1", "c1.xhtml", null, 0)))
        l.cacheToc("item-1", BookFormat.EPUB, pageCount = 0, entries = emptyList())
        advanceUntilIdle()

        assertEquals(1, tocCache.puts.size)
        assertEquals(listOf(BookTocEntry("Ch 1", "c1.xhtml", null, 0)), tocCache.puts.single().entries)
    }
}

/** Always-resolving content seam; `fail` simulates the streaming-fetch error path. */
private class FakeContentResolver : BookContentResolver {
    var fail = false

    override suspend fun resolve(
        itemId: String,
        fileName: String?,
        format: BookFormat,
        downloadUrl: String,
        accessToken: String?,
        onProgress: (BookDownloadProgress) -> Unit,
    ): ResolvedBook {
        if (fail) throw IllegalStateException("fetch failed")
        onProgress(BookDownloadProgress(bytesDownloaded = 50, totalBytes = 100))
        return ResolvedBook.ReaderCache("cache/book".toPath(), downloadedNow = true)
    }
}

/** Opens a fixed-page-count document; records the opener's page re-anchors. */
private class FakeDocumentOpener(private val pageCount: Int) : BookDocumentOpener {
    var fail = false
    var opens = 0
    val anchoredPages = mutableListOf<Int>()

    override suspend fun open(path: Path, format: BookFormat): BookDocument? {
        if (fail) return null
        opens++
        return object : BookDocument {
            override val pageCount: Int = this@FakeDocumentOpener.pageCount
            override suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap? = null
            override fun onPageChanged(pageIndex: Int) {
                anchoredPages += pageIndex
            }
            override fun close() {}
        }
    }
}

/** Value fake of the TOC cache: a write log, reads always empty. */
private class RecordingTocCache : BookTocCacheRepository {
    data class Put(
        val itemId: String,
        val format: BookFormat,
        val pageCount: Int,
        val entries: List<BookTocEntry>,
    )

    val puts = mutableListOf<Put>()

    override fun observeToc(itemId: String): Flow<BookTocCache?> = flowOf(null)

    override suspend fun getToc(itemId: String): BookTocCache? = null

    override suspend fun putToc(itemId: String, format: BookFormat, pageCount: Int, entries: List<BookTocEntry>) {
        puts += Put(itemId, format, pageCount, entries)
    }

    override suspend fun deleteToc(itemId: String) {}
}
