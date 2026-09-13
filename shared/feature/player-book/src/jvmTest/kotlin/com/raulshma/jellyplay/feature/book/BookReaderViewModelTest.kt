package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import androidx.compose.ui.graphics.ImageBitmap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path
import okio.Path.Companion.toPath

/**
 * Pins the Wave 3 mark/jump/resume logic of [BookReaderViewModel]: the
 * bookmark toggle encodings (paged page-ticks vs reflowable percent+CFI),
 * match-and-remove semantics, paged jumps, selection → annotation, and the
 * exact-resume CFI round trip through the ReaderStore map. Repository and
 * seam collaborators are value fakes / mockk stubs (module jvmTest
 * conventions); timing rides a StandardTestDispatcher main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookReaderViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var readerStore: ReaderStore
    private lateinit var annotationsRepository: FakeReaderAnnotationsRepository
    private lateinit var contentResolver: FakeBookContentResolver
    private val readerSlice = MutableStateFlow(ReaderSlice())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk()
        playbackRepository = mockk()
        readerStore = mockk()
        every { readerStore.reader } returns readerSlice
        annotationsRepository = FakeReaderAnnotationsRepository()
        contentResolver = FakeBookContentResolver()
        every { playbackRepository.getBookDownloadUrl(any()) } returns "https://server/download"
        every { playbackRepository.getAccessToken() } returns "token"
        every { readerStore.lastCfi(any()) } returns null
        coEvery { readerStore.setLastCfi(any(), any()) } returns Unit
        coEvery { playbackRepository.reportBookProgress(any(), any(), any()) } returns Result.success(Unit)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        documentOpener: BookDocumentOpener = FakeBookDocumentOpener(pageCount = 3),
    ): BookReaderViewModel = BookReaderViewModel(
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        readerStore = readerStore,
        annotationsRepository = annotationsRepository,
        contentResolver = contentResolver,
        documentOpener = documentOpener,
        pdfOutlineParser = PdfOutlineParser(),
        flushScope = CoroutineScope(UnconfinedTestDispatcher(mainDispatcher.scheduler)),
    )

    private fun detail(path: String, positionTicks: Long = 0L): MediaDetail =
        MediaDetail(
            item = MediaItem(id = "item-1", name = "A Book", mediaType = MediaType.BOOK),
            path = path,
            playbackPositionTicks = positionTicks,
        )

    private fun stubDetail(path: String, positionTicks: Long = 0L) {
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(detail(path, positionTicks))
    }

    // ------------------------------------------------------------------
    // Bookmarks — paged encoding + toggle semantics
    // ------------------------------------------------------------------

    @Test
    fun `paged bookmark toggle adds page ticks then removes the same row`() = runTest(mainDispatcher) {
        stubDetail("comic.cbz", positionTicks = BookProgressPolicy.pageToTicks(1))

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()
        val ready = assertIs<BookReaderUiState.Ready>(vm.uiState.value)
        assertEquals(BookFormat.CBZ, (ready.content as ReadyContent.Paged).format)

        assertFalse(vm.hasBookmarkAtCurrentPosition())
        vm.toggleBookmarkAtCurrentPosition()
        advanceUntilIdle()

        // Paged rows: ticks = pageToTicks(currentPage), no CFI, empty label.
        val added = annotationsRepository.addedBookmarks.single()
        assertEquals(BookProgressPolicy.pageToTicks(1), added.positionTicks)
        assertNull(added.cfi)
        assertEquals("", added.chapterLabel)
        assertTrue(vm.hasBookmarkAtCurrentPosition())

        // Toggling again removes the same bookmark, not a duplicate.
        vm.toggleBookmarkAtCurrentPosition()
        advanceUntilIdle()
        assertTrue(annotationsRepository.addedBookmarks.size == 1)
        assertTrue(annotationsRepository.removedBookmarkIds.isNotEmpty())
        assertFalse(vm.hasBookmarkAtCurrentPosition())
    }

    @Test
    fun `reflowable bookmark encodes percent cfi and chapter label`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.onEpubRelocated(
            com.raulshma.jellyplay.feature.book.epub.EpubRelocation(
                percent = 0.5,
                chapterLabel = "Chapter 2",
                remainingPages = 3,
                cfi = "epubcfi(/6/8!/4/2)",
            ),
        )
        advanceUntilIdle()
        vm.toggleBookmarkAtCurrentPosition()
        advanceUntilIdle()

        val added = annotationsRepository.addedBookmarks.single()
        assertEquals(BookProgressPolicy.percentToTicks(0.5), added.positionTicks)
        assertEquals("epubcfi(/6/8!/4/2)", added.cfi)
        assertEquals("Chapter 2", added.chapterLabel)
        assertTrue(vm.hasBookmarkAtCurrentPosition())

        // Same relocation → same CFI → the second toggle removes it.
        vm.toggleBookmarkAtCurrentPosition()
        advanceUntilIdle()
        assertFalse(vm.hasBookmarkAtCurrentPosition())
    }

    @Test
    fun `null-cfi reflowable bookmark matches by encoded percent`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        // A pre-Wave-3 row: percent ticks, no CFI anchor.
        annotationsRepository.seedBookmark(
            ReaderBookmark(
                id = 42L,
                itemId = "item-1",
                positionTicks = BookProgressPolicy.percentToTicks(0.5),
                cfi = null,
                chapterLabel = "Old",
                createdAt = 0L,
            ),
        )

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()
        vm.onEpubRelocated(
            com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.5, "Chapter 1", null, cfi = null),
        )
        advanceUntilIdle()

        assertTrue(vm.hasBookmarkAtCurrentPosition())
        vm.toggleBookmarkAtCurrentPosition()
        advanceUntilIdle()
        assertEquals(listOf(42L), annotationsRepository.removedBookmarkIds)
    }

    // ------------------------------------------------------------------
    // Jumps
    // ------------------------------------------------------------------

    @Test
    fun `paged jump resolves bookmark ticks into the pager`() = runTest(mainDispatcher) {
        stubDetail("comic.cbz", positionTicks = 0L)

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.jumpToBookmark(
            ReaderBookmark(
                id = 7L,
                itemId = "item-1",
                positionTicks = BookProgressPolicy.pageToTicks(2),
                cfi = null,
                chapterLabel = "",
                createdAt = 0L,
            ),
        )
        advanceUntilIdle()

        val ready = assertIs<BookReaderUiState.Ready>(vm.uiState.value)
        assertEquals(2, (ready.content as ReadyContent.Paged).currentPage)
    }

    @Test
    fun `reflowable jump is a no-op for a null-cfi row`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.jumpToBookmark(
            ReaderBookmark(
                id = 7L,
                itemId = "item-1",
                positionTicks = BookProgressPolicy.percentToTicks(0.5),
                cfi = null,
                chapterLabel = "",
                createdAt = 0L,
            ),
        )
        advanceUntilIdle()
        // The reflowable branch deliberately does nothing — the screen drives
        // host.goToCfi for CFI rows; nothing here should have fallen apart.
        assertIs<ReadyContent.Reflowable>((vm.uiState.value as BookReaderUiState.Ready).content)
    }

    // ------------------------------------------------------------------
    // Selection → annotation
    // ------------------------------------------------------------------

    @Test
    fun `selection becomes an annotation with anchor text and chapter label`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()
        vm.onEpubRelocated(
            com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.25, "One", null, cfi = "epubcfi(/6/4)"),
        )
        advanceUntilIdle()

        vm.onEpubSelection("epubcfi(/6/4!/4/2:0..28)", "the chosen words")
        assertEquals("the chosen words", vm.selection.value?.text)
        assertEquals(null, vm.annotationAtSelection())

        vm.addSelectionAnnotation(
            style = ReaderAnnotationStyle.UNDERLINE,
            color = ReaderAnnotationColor.GREEN,
            note = "remember this",
        )
        advanceUntilIdle()

        val added = annotationsRepository.addedAnnotations.single()
        assertEquals("epubcfi(/6/4!/4/2:0..28)", added.cfi)
        assertEquals("the chosen words", added.anchorText)
        assertEquals("One", added.chapterLabel)
        assertEquals("remember this", added.note)
        assertEquals(ReaderAnnotationStyle.UNDERLINE, added.style)
        assertEquals(ReaderAnnotationColor.GREEN, added.color)
        // The live selection closed with the add (the screen clears the JS side).
        assertNull(vm.selection.value)
    }

    @Test
    fun `annotation at selection resolves the edit-mode row target`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        annotationsRepository.seedAnnotation(
            ReaderAnnotation(
                id = 9L,
                itemId = "item-1",
                cfi = "epubcfi(/6/4!/4/2:0..28)",
                style = ReaderAnnotationStyle.HIGHLIGHT,
                color = ReaderAnnotationColor.YELLOW,
                anchorText = "old",
                note = null,
                chapterLabel = "",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.onEpubSelection("epubcfi(/6/4!/4/2:0..28)", "old")
        assertEquals(9L, vm.annotationAtSelection()?.id)
    }

    // ------------------------------------------------------------------
    // Exact resume
    // ------------------------------------------------------------------

    @Test
    fun `stored last cfi rides the reflowable ready content`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = BookProgressPolicy.percentToTicks(0.3))
        every { readerStore.lastCfi("item-1") } returns "epubcfi(/6/12!/4/2)"

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        val content = assertIs<ReadyContent.Reflowable>((vm.uiState.value as BookReaderUiState.Ready).content)
        assertEquals("epubcfi(/6/12!/4/2)", content.resumeCfi)
        assertEquals(0.3, content.resumePercent)
    }

    @Test
    fun `relocated cfi persists through the debounced flush`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()
        vm.onEpubRelocated(
            com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.4, "Two", null, cfi = "epubcfi(/6/20)"),
        )
        advanceTimeBy(1_000)
        advanceUntilIdle()

        coVerify { readerStore.setLastCfi("item-1", "epubcfi(/6/20)") }
    }
}

/** Value fake of the marks repository: StateFlow-backed lists + a write log. */
private class FakeReaderAnnotationsRepository : ReaderAnnotationsRepository {
    val addedBookmarks = mutableListOf<ReaderBookmark>()
    val addedAnnotations = mutableListOf<ReaderAnnotation>()
    val removedBookmarkIds = mutableListOf<Long>()
    private val bookmarks = MutableStateFlow<List<ReaderBookmark>>(emptyList())
    private val annotations = MutableStateFlow<List<ReaderAnnotation>>(emptyList())
    private var nextId = 1L

    fun seedBookmark(bookmark: ReaderBookmark) {
        bookmarks.value = bookmarks.value + bookmark
    }

    fun seedAnnotation(annotation: ReaderAnnotation) {
        annotations.value = annotations.value + annotation
    }

    override fun observeBookmarks(itemId: String): Flow<List<ReaderBookmark>> = bookmarks

    override fun observeAnnotations(itemId: String): Flow<List<ReaderAnnotation>> = annotations

    override suspend fun addBookmark(itemId: String, positionTicks: Long, cfi: String?, chapterLabel: String) {
        val row = ReaderBookmark(nextId++, itemId, positionTicks, cfi, chapterLabel, createdAt = 0L)
        addedBookmarks.add(row)
        bookmarks.value = bookmarks.value + row
    }

    override suspend fun removeBookmark(id: Long) {
        removedBookmarkIds.add(id)
        bookmarks.value = bookmarks.value.filterNot { it.id == id }
    }

    override suspend fun addAnnotation(
        itemId: String,
        cfi: String,
        style: ReaderAnnotationStyle,
        color: ReaderAnnotationColor,
        anchorText: String,
        note: String?,
        chapterLabel: String,
    ) {
        val row = ReaderAnnotation(
            id = nextId++, itemId = itemId, cfi = cfi, style = style, color = color,
            anchorText = anchorText, note = note, chapterLabel = chapterLabel,
            createdAt = 0L, updatedAt = 0L,
        )
        addedAnnotations.add(row)
        annotations.value = annotations.value + row
    }

    override suspend fun updateAnnotation(
        id: Long,
        note: String?,
        color: ReaderAnnotationColor?,
        style: ReaderAnnotationStyle?,
    ) {
        annotations.value = annotations.value.map {
            if (it.id == id) {
                it.copy(
                    note = note ?: it.note,
                    color = color ?: it.color,
                    style = style ?: it.style,
                    updatedAt = 1L,
                )
            } else {
                it
            }
        }
    }

    override suspend fun deleteAnnotation(id: Long) {
        annotations.value = annotations.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllForItem(itemId: String) {
        bookmarks.value = emptyList()
        annotations.value = emptyList()
    }

    override fun exportMarkdown(
        itemId: String?,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String = "markdown"

    override fun exportJson(
        itemId: String?,
        title: String,
        bookmarks: List<ReaderBookmark>,
        annotations: List<ReaderAnnotation>,
    ): String = "{}"
}

/** Always-resolving content seam: the file need not exist for these paths. */
private class FakeBookContentResolver : BookContentResolver {
    override suspend fun resolve(
        itemId: String,
        fileName: String?,
        format: BookFormat,
        downloadUrl: String,
        accessToken: String?,
        onProgress: (BookDownloadProgress) -> Unit,
    ): ResolvedBook = ResolvedBook.ReaderCache("cache/$fileName".toPath(), downloadedNow = false)
}

private class FakeBookDocumentOpener(private val pageCount: Int) : BookDocumentOpener {
    override suspend fun open(path: Path, format: BookFormat): BookDocument = object : BookDocument {
        override val pageCount: Int = this@FakeBookDocumentOpener.pageCount
        override suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap? = null
        override fun close() {}
    }
}
