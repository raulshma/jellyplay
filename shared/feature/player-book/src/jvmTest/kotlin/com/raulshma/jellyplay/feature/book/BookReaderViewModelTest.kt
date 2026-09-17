package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotation
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationColor
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationStyle
import com.raulshma.jellyplay.core.data.repository.ReaderAnnotationsRepository
import com.raulshma.jellyplay.core.data.repository.ReaderBookmark
import com.raulshma.jellyplay.core.datastore.reader.PerBookAppearance
import com.raulshma.jellyplay.core.datastore.reader.ReaderFontFamily
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import com.raulshma.jellyplay.core.model.BookFormat
import com.raulshma.jellyplay.core.model.BookProgressPolicy
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.book.epub.EpubAnnotationSpec
import com.raulshma.jellyplay.feature.book.epub.EpubReaderHandle
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
    private lateinit var readerPreferences: ReaderPreferences
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
        // Wave 4 preference setters (strict mockk needs the stubs up front).
        coEvery { readerStore.setReaderTheme(any()) } returns Unit
        coEvery { readerStore.setReaderFontSizePx(any()) } returns Unit
        coEvery { readerStore.setPerBookAppearance(any(), any()) } returns Unit
        coEvery { readerStore.setFontFamily(any()) } returns Unit
        coEvery { readerStore.setLineHeightPct(any()) } returns Unit
        coEvery { readerStore.setMarginPct(any()) } returns Unit
        coEvery { readerStore.setJustify(any()) } returns Unit
        coEvery { readerStore.setScrollMode(any()) } returns Unit
        coEvery { readerStore.setBrightnessPct(any()) } returns Unit
        coEvery { readerStore.setVolumeKeyPaging(any()) } returns Unit
        coEvery { readerStore.setAnimatedPageTurns(any()) } returns Unit
        coEvery { readerStore.setReadingSpeedWpm(any()) } returns Unit
        coEvery { readerStore.setSpeechRate(any()) } returns Unit
        coEvery { readerStore.setSpeechPitch(any()) } returns Unit
        coEvery { readerStore.setAutoScrollSpeedPxPerSec(any()) } returns Unit
        coEvery { readerStore.setReadingDirection(any(), any()) } returns Unit
        every { readerStore.perBookAppearance(any()) } returns null
        readerPreferences = ReaderPreferences(
            store = readerStore,
            scope = CoroutineScope(StandardTestDispatcher(mainDispatcher.scheduler)),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        documentOpener: BookDocumentOpener = FakeBookDocumentOpener(pageCount = 3),
        speechEngine: BookSpeechEngine = NoopBookSpeechEngine,
        playbackFocus: com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus =
            com.raulshma.jellyplay.core.data.playback.focus.NoopPlaybackFocus,
    ): BookReaderViewModel = BookReaderViewModel(
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        preferences = readerPreferences,
        annotationsRepository = annotationsRepository,
        contentResolver = contentResolver,
        documentOpener = documentOpener,
        pdfOutlineParser = PdfOutlineParser(),
        speechEngine = speechEngine,
        playbackFocus = playbackFocus,
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

        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(
                    percent = 0.5,
                    chapterLabel = "Chapter 2",
                    remainingPages = 3,
                    cfi = "epubcfi(/6/8!/4/2)",
                ),
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
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.5, "Chapter 1", null, cfi = null),
            ),
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

    /**
     * Regresses the dead Contents deep-link for paged books: openBook used to
     * clear pendingJumpPage before the paged-resume branch read it, so the
     * jump page never took effect and the reader opened at the server resume
     * position instead.
     */
    @Test
    fun `paged deep link jumpPage outranks the server resume position`() = runTest(mainDispatcher) {
        stubDetail("comic.cbz", positionTicks = BookProgressPolicy.pageToTicks(1))

        val vm = viewModel()
        vm.load("item-1", jumpPage = 2)
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
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.25, "One", null, cfi = "epubcfi(/6/4)"),
            ),
        )
        advanceUntilIdle()

        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Selected(
                "epubcfi(/6/4!/4/2:0..28)",
                "the chosen words",
            ),
        )
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

        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Selected(
                "epubcfi(/6/4!/4/2:0..28)",
                "old",
            ),
        )
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
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.4, "Two", null, cfi = "epubcfi(/6/20)"),
            ),
        )
        advanceTimeBy(1_000)
        advanceUntilIdle()

        coVerify { readerStore.setLastCfi("item-1", "epubcfi(/6/20)") }
    }

    // ------------------------------------------------------------------
    // The single position fold (both reflowable position event kinds)
    // ------------------------------------------------------------------

    @Test
    fun `location flow carries the boot resume percent before any event`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = BookProgressPolicy.percentToTicks(0.3))

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        // Boot carrier: seeded at open, no percent/relocated event needed.
        val location = vm.currentEpubLocation.value
        assertEquals(0.3, location?.percent)
        assertEquals("", location?.chapterLabel)
        assertNull(location?.cfi)
    }

    @Test
    fun `percent and relocated events ride one report path`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        // The bare percent event folds through the same clamp + location +
        // debounced-report sequence a relocation does.
        vm.onEpubEvent(com.raulshma.jellyplay.feature.book.epub.EpubEvent.Percent(0.4))
        advanceTimeBy(1_000)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            playbackRepository.reportBookProgress("item-1", BookProgressPolicy.percentToTicks(0.4), any())
        }
        assertEquals(0.4, vm.currentEpubLocation.value?.percent)

        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.6, "Two", 1, cfi = "epubcfi(/6/8)"),
            ),
        )
        advanceTimeBy(1_000)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            playbackRepository.reportBookProgress("item-1", BookProgressPolicy.percentToTicks(0.6), any())
        }
        coVerify { readerStore.setLastCfi("item-1", "epubcfi(/6/8)") }
        assertEquals(0.6, vm.currentEpubLocation.value?.percent)
        assertEquals("Two", vm.currentEpubLocation.value?.chapterLabel)
    }

    // ------------------------------------------------------------------
    // Wave 4: typography + behavior setters, per-book appearance routing
    // ------------------------------------------------------------------

    @Test
    fun `typography and behavior setters write clamped values through the store`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        val prefs = vm.preferences
        prefs.setFontFamily(ReaderFontFamily.SERIF)
        prefs.setLineHeightPct(150)
        prefs.setMarginPct(20)
        prefs.setJustify(true)
        prefs.setScrollMode(true)
        prefs.setBrightnessPct(40)
        prefs.setVolumeKeyPaging(true)
        prefs.setAnimatedPageTurns(false)
        prefs.setReadingSpeedWpm(300)
        // Out-of-band inputs clamp into the store bands.
        prefs.setLineHeightPct(999)
        prefs.setMarginPct(-5)
        prefs.setBrightnessPct(120)
        prefs.setReadingSpeedWpm(5)
        advanceUntilIdle()

        coVerify { readerStore.setFontFamily(ReaderFontFamily.SERIF) }
        coVerify { readerStore.setLineHeightPct(150) }
        coVerify { readerStore.setLineHeightPct(200) }
        coVerify { readerStore.setMarginPct(20) }
        coVerify { readerStore.setMarginPct(0) }
        coVerify { readerStore.setJustify(true) }
        coVerify { readerStore.setScrollMode(true) }
        coVerify { readerStore.setBrightnessPct(40) }
        coVerify { readerStore.setBrightnessPct(100) }
        coVerify { readerStore.setVolumeKeyPaging(true) }
        coVerify { readerStore.setAnimatedPageTurns(false) }
        coVerify { readerStore.setReadingSpeedWpm(300) }
        coVerify { readerStore.setReadingSpeedWpm(100) }
    }

    @Test
    fun `theme write routes into the override when one is active`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        readerSlice.value = ReaderSlice(
            readerTheme = ReaderTheme.DARK,
            readerFontSizePx = 17,
            perBookAppearance = mapOf("item-1" to PerBookAppearance(theme = ReaderTheme.DARK, fontSizePx = 19)),
        )

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.preferences.setTheme(ReaderTheme.SEPIA)
        vm.preferences.adjustFontSize(+2)
        advanceUntilIdle()

        // Per-book mode: both writes land in the override; the globals stay untouched.
        coVerify {
            readerStore.setPerBookAppearance("item-1", PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 21))
        }
        coVerify(exactly = 0) { readerStore.setReaderTheme(any()) }
        coVerify(exactly = 0) { readerStore.setReaderFontSizePx(any()) }
    }

    @Test
    fun `theme write goes global without an override`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.preferences.setTheme(ReaderTheme.LIGHT)
        vm.preferences.adjustFontSize(+1)
        advanceUntilIdle()

        coVerify { readerStore.setReaderTheme(ReaderTheme.LIGHT) }
        coVerify { readerStore.setReaderFontSizePx(18) }
        coVerify(exactly = 0) { readerStore.setPerBookAppearance(any(), any()) }
    }

    @Test
    fun `effective flows prefer the item override`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        readerSlice.value = ReaderSlice(
            readerTheme = ReaderTheme.DARK,
            readerFontSizePx = 17,
            perBookAppearance = mapOf("item-1" to PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 22)),
        )

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        val snap = vm.prefs.value
        assertEquals(ReaderTheme.SEPIA, snap.effective.theme)
        assertEquals(22, snap.effective.fontSizePx)
        assertEquals(PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 22), snap.perBook)
    }

    @Test
    fun `switching per-book off syncs the effective values into the globals then clears`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        readerSlice.value = ReaderSlice(
            readerTheme = ReaderTheme.DARK,
            readerFontSizePx = 17,
            perBookAppearance = mapOf("item-1" to PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 22)),
        )

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.preferences.setUsePerBookAppearance(false)
        advanceUntilIdle()

        // The in-session look survives the switch: effective → globals, then the override clears.
        coVerify { readerStore.setReaderTheme(ReaderTheme.SEPIA) }
        coVerify { readerStore.setReaderFontSizePx(22) }
        coVerify { readerStore.setPerBookAppearance("item-1", null) }
    }

    @Test
    fun `switching per-book on seeds the override with the effective values`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        readerSlice.value = ReaderSlice(readerTheme = ReaderTheme.LIGHT, readerFontSizePx = 20)

        val vm = viewModel()
        vm.load("item-1")
        advanceUntilIdle()

        vm.preferences.setUsePerBookAppearance(true)
        advanceUntilIdle()

        coVerify {
            readerStore.setPerBookAppearance("item-1", PerBookAppearance(theme = ReaderTheme.LIGHT, fontSizePx = 20))
        }
        coVerify(exactly = 0) { readerStore.setReaderTheme(any()) }
    }

    // ------------------------------------------------------------------
    // Wave 5: read aloud (context requests, chapter continuation, book end)
    // ------------------------------------------------------------------

    @Test
    fun `read aloud requests the context, speaks, advances chapters and finishes at book end`() =
        runTest(mainDispatcher) {
            stubDetail("novel.epub", positionTicks = 0L)
            val engine = FakeBookSpeechEngine()
            val vm = viewModel(speechEngine = engine)
            val host = FakeEpubHost()
            val port = FakeReaderSession(host)
            vm.attachReaderSession(port)
            vm.load("item-1")
            advanceUntilIdle()
            vm.onEpubEvent(
                com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                    com.raulshma.jellyplay.feature.book.epub.EpubRelocation(
                        0.1, "One", null, cfi = "epubcfi(/6/4!/4/2)",
                    ),
                ),
            )
            advanceUntilIdle()

            vm.startReadAloud()
            advanceUntilIdle()
            // Engine configured from the persisted defaults; the context
            // request anchors at the relocated CFI, straight through the
            // speech controller's host seam (no command channel).
            assertEquals(100, engine.configuredRate)
            assertEquals(100, engine.configuredPitch)
            assertEquals(listOf("speechContext:epubcfi(/6/4!/4/2)"), host.log)
            assertTrue(port.follows.isEmpty())

            val chapterOne = listOf(
                com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph("epubcfi(/6/4!/4/10)", "first"),
                com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph("epubcfi(/6/4!/4/11)", "second"),
            )
            vm.onEpubEvent(
                com.raulshma.jellyplay.feature.book.epub.EpubEvent.SpeechContext(chapterOne),
            )
            advanceUntilIdle()
            assertEquals(listOf("first"), engine.spoken)
            assertEquals("epubcfi(/6/4!/4/10)", port.follows.single())

            engine.complete()
            assertEquals(listOf("first", "second"), engine.spoken)
            engine.complete()
            advanceUntilIdle()
            // Chapter end: the controller turns the host page itself, then
            // the context re-request rides the turn's relocation
            // (null = current chapter).
            assertTrue(host.log.any { it == "next" })
            vm.onEpubEvent(
                com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                    com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.2, "Two", null, cfi = "epubcfi(/6/6)"),
                ),
            )
            advanceUntilIdle()
            assertEquals("speechContext:null", host.log.last { it.startsWith("speechContext:") })

            vm.onEpubEvent(
                com.raulshma.jellyplay.feature.book.epub.EpubEvent.SpeechContext(
                    listOf(com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph("epubcfi(/6/6!/4/1)", "next chapter")),
                ),
            )
            advanceUntilIdle()
            assertEquals(listOf("first", "second", "next chapter"), engine.spoken)

            // Book end: the last chapter's paragraphs exhaust, the turn never
            // relocates, the timeout re-requests and the SAME chapter comes
            // back — identity guard finishes the session.
            engine.complete()
            advanceUntilIdle()
            advanceTimeBy(2_000)
            advanceUntilIdle()
            vm.onEpubEvent(
                com.raulshma.jellyplay.feature.book.epub.EpubEvent.SpeechContext(
                    listOf(com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph("epubcfi(/6/6!/4/1)", "next chapter")),
                ),
            )
            advanceUntilIdle()
            assertFalse(vm.speechState.value.active)
        }

    @Test
    fun `start read aloud is a no-op without an engine`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val vm = viewModel() // Noop engine: UNAVAILABLE
        val host = FakeEpubHost()
        val port = FakeReaderSession(host)
        vm.attachReaderSession(port)
        vm.load("item-1")
        advanceUntilIdle()

        vm.startReadAloud()
        advanceUntilIdle()
        assertFalse(vm.speechState.value.active)
        assertTrue(host.log.isEmpty())
        assertTrue(port.follows.isEmpty())
        assertEquals(0, port.sleepFires)
    }

    @Test
    fun `speech rate and pitch writes clamp and configure the engine`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val engine = FakeBookSpeechEngine()
        val vm = viewModel(speechEngine = engine)
        vm.load("item-1")
        advanceUntilIdle()

        vm.setSpeechRate(500)
        vm.setSpeechPitch(5)
        advanceUntilIdle()

        assertEquals(200, engine.configuredRate)
        assertEquals(50, engine.configuredPitch)
        coVerify { readerStore.setSpeechRate(200) }
        coVerify { readerStore.setSpeechPitch(50) }
    }

    // ------------------------------------------------------------------
    // Wave 5: sleep timer
    // ------------------------------------------------------------------

    @Test
    fun `end of chapter sleep timer stops read aloud and announces the stop`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val engine = FakeBookSpeechEngine()
        val vm = viewModel(speechEngine = engine)
        val host = FakeEpubHost()
        val port = FakeReaderSession(host)
        vm.attachReaderSession(port)
        vm.load("item-1")
        advanceUntilIdle()
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.1, "One", null, cfi = "epubcfi(/6/4)"),
            ),
        )
        vm.startReadAloud()
        advanceUntilIdle()
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.SpeechContext(
                listOf(com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph("epubcfi(/6/4!/4/1)", "para")),
            ),
        )
        advanceUntilIdle()
        assertEquals(listOf("para"), engine.spoken)

        vm.startSleepTimer(ReaderSleepOption.EndOfChapter)
        assertTrue(vm.sleepTimerState.value.running)

        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.2, "Two", null, cfi = "epubcfi(/6/6)"),
            ),
        )
        advanceUntilIdle()
        assertFalse(vm.sleepTimerState.value.running)
        assertFalse(vm.speechState.value.active)
        assertEquals(1, port.sleepFires, "the session's auto-scroll stop was announced")
        assertTrue(engine.stopCount > 0)
    }

    @Test
    fun `read aloud claims the focus floor and releases it on stop`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val engine = FakeBookSpeechEngine()
        val claims = mutableListOf<String>()
        val focus = object : com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus {
            override val claimState =
                kotlinx.coroutines.flow.MutableStateFlow<com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState>(
                    com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState.Idle,
                )
            override fun acquire(
                claimant: com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId,
            ) = claims.add("acquire:$claimant").let {
                com.raulshma.jellyplay.core.data.playback.focus.FocusOutcome.Granted
            }
            override fun release(
                claimant: com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId,
            ) {
                claims.add("release:$claimant")
            }
        }
        val vm = viewModel(speechEngine = engine, playbackFocus = focus)
        vm.load("item-1")
        advanceUntilIdle()
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.1, "One", null, cfi = "epubcfi(/6/4)"),
            ),
        )
        advanceUntilIdle()

        vm.startReadAloud()
        advanceUntilIdle()
        assertTrue(claims.contains("acquire:READ_ALOUD"), "the floor is claimed before any speech")

        vm.stopReadAloud()
        advanceUntilIdle()
        assertTrue(claims.contains("release:READ_ALOUD"), "the floor is released with the session")
    }

    /**
     * Pins the init collector wiring the availability flow to the speech
     * controller: an engine that reports UNAVAILABLE while the loop is live
     * (service died, language vanished) ends the session — and a later
     * recovery never resurrects it (resume is the user's tap).
     */
    @Test
    fun `engine going UNAVAILABLE mid session ends the speech session`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val engine = FakeBookSpeechEngine()
        val vm = viewModel(speechEngine = engine)
        val host = FakeEpubHost()
        val port = FakeReaderSession(host)
        vm.attachReaderSession(port)
        vm.load("item-1")
        advanceUntilIdle()
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.1, "One", null, cfi = "epubcfi(/6/4)"),
            ),
        )
        vm.startReadAloud()
        advanceUntilIdle()
        vm.onEpubEvent(
            com.raulshma.jellyplay.feature.book.epub.EpubEvent.SpeechContext(
                listOf(com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph("epubcfi(/6/4!/4/1)", "para")),
            ),
        )
        advanceUntilIdle()
        assertTrue(vm.speechState.value.active, "session is live before the engine loss")

        engine.availability.value = BookSpeechAvailability.UNAVAILABLE
        advanceUntilIdle()
        assertFalse(vm.speechState.value.active, "engineLost ended the session")

        engine.availability.value = BookSpeechAvailability.AVAILABLE
        advanceUntilIdle()
        assertFalse(vm.speechState.value.active, "recovery never resurrects the session")
    }

    /**
     * Pins the displaced-claimant collector: music claiming the floor while
     * read-aloud plays pauses the loop, and the claim releasing does NOT
     * auto-resume — the user's next play tap is the only way back (ADR-0004).
     */
    @Test
    fun `a displaced focus claimant pauses read aloud and release never auto resumes`() =
        runTest(mainDispatcher) {
            stubDetail("novel.epub", positionTicks = 0L)
            val engine = FakeBookSpeechEngine()
            val claimState = MutableStateFlow<com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState>(
                com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState.Idle,
            )
            val focus = object : com.raulshma.jellyplay.core.data.playback.focus.PlaybackFocus {
                override val claimState = claimState
                override fun acquire(
                    claimant: com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId,
                ) = com.raulshma.jellyplay.core.data.playback.focus.FocusOutcome.Granted
                override fun release(
                    claimant: com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId,
                ) {}
            }
            val vm = viewModel(speechEngine = engine, playbackFocus = focus)
            val host = FakeEpubHost()
            val port = FakeReaderSession(host)
            vm.attachReaderSession(port)
            vm.load("item-1")
            advanceUntilIdle()
            vm.onEpubEvent(
                com.raulshma.jellyplay.feature.book.epub.EpubEvent.Relocated(
                    com.raulshma.jellyplay.feature.book.epub.EpubRelocation(0.1, "One", null, cfi = "epubcfi(/6/4)"),
                ),
            )
            vm.startReadAloud()
            advanceUntilIdle()
            vm.onEpubEvent(
                com.raulshma.jellyplay.feature.book.epub.EpubEvent.SpeechContext(
                    listOf(com.raulshma.jellyplay.feature.book.epub.EpubSpeechParagraph("epubcfi(/6/4!/4/1)", "para")),
                ),
            )
            advanceUntilIdle()
            assertTrue(vm.speechState.value.active)
            assertFalse(vm.speechState.value.paused)

            claimState.value =
                com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState.Held(
                    com.raulshma.jellyplay.core.data.playback.focus.PlaybackSurfaceId.MUSIC,
                )
            advanceUntilIdle()
            assertTrue(vm.speechState.value.active, "the session survives the displacement")
            assertTrue(vm.speechState.value.paused, "but the loop pauses")

            claimState.value = com.raulshma.jellyplay.core.data.playback.focus.FocusClaimState.Idle
            advanceUntilIdle()
            assertTrue(vm.speechState.value.paused, "release never auto-resumes the loop")
        }

    @Test
    fun `timed sleep timer fires after the countdown`() = runTest(mainDispatcher) {
        stubDetail("novel.epub", positionTicks = 0L)
        val vm = viewModel()
        val port = FakeReaderSession(null)
        vm.attachReaderSession(port)
        vm.load("item-1")
        advanceUntilIdle()

        vm.startSleepTimer(ReaderSleepOption.Timed(minutes = 5))
        assertTrue(vm.sleepTimerState.value.running)
        advanceTimeBy(5 * 60_000 + 1_000)
        advanceUntilIdle()
        assertFalse(vm.sleepTimerState.value.running)
        assertEquals(1, port.sleepFires)
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

/** Recording host: the speech continuation's direct seam through the session port. */
private class FakeEpubHost : EpubReaderHandle {
    val log = mutableListOf<String>()
    override val viewerDownloadProgress: androidx.compose.runtime.State<Float?> =
        androidx.compose.runtime.mutableStateOf<Float?>(null)
    override fun next() { log.add("next") }
    override fun prev() { log.add("prev") }
    override fun goTo(href: String) { log.add("goTo:$href") }
    override fun goToCfi(cfi: String) { log.add("goToCfi:$cfi") }
    override fun setFlow(scrolled: Boolean) { log.add("setFlow:$scrolled") }
    override fun applyAnnotations(entries: List<EpubAnnotationSpec>) { log.add("apply:${entries.size}") }
    override fun addAnnotation(entry: EpubAnnotationSpec) { log.add("add:${entry.cfi}") }
    override fun removeAnnotation(cfi: String) { log.add("remove:$cfi") }
    override fun clearSelection() { log.add("clearSelection") }
    override fun search(query: String, token: Int) { log.add("search:$query/$token") }
    override fun requestSpeechContext(cfi: String?) { log.add("speechContext:$cfi") }
    override fun setAutoScroll(enabled: Boolean, pxPerSec: Int) { log.add("autoScroll:$enabled/$pxPerSec") }
}

/** Recording session port: the VM's window onto the reflowable session (host + hooks). */
private class FakeReaderSession(
    override val host: EpubReaderHandle?,
) : ReaderSessionPort {
    val follows = mutableListOf<String>()
    var sleepFires = 0
    override fun followSpeech(cfi: String) { follows.add(cfi) }
    override fun sleepTimerFired() { sleepFires++ }
}

private class FakeBookDocumentOpener(private val pageCount: Int) : BookDocumentOpener {
    override suspend fun open(path: Path, format: BookFormat): BookDocument = object : BookDocument {
        override val pageCount: Int = this@FakeBookDocumentOpener.pageCount
        override suspend fun renderPage(pageIndex: Int, widthPx: Int): ImageBitmap? = null
        override fun close() {}
    }
}

/** Available-by-default speech engine value fake: records speaks/configs, manual completion. */
private class FakeBookSpeechEngine : BookSpeechEngine {
    override val availability = MutableStateFlow(BookSpeechAvailability.AVAILABLE)
    val spoken = mutableListOf<String>()
    var configuredRate = 100
    var configuredPitch = 100
    var stopCount = 0
    private var pendingDone: (() -> Unit)? = null

    override fun configure(ratePercent: Int, pitchPercent: Int) {
        configuredRate = ratePercent
        configuredPitch = pitchPercent
    }

    override fun speak(text: String, onDone: () -> Unit) {
        spoken.add(text)
        pendingDone = onDone
    }

    override fun stop() {
        stopCount++
        pendingDone = null
    }

    override fun shutdown() {}

    fun complete() {
        val done = pendingDone
        pendingDone = null
        done?.invoke()
    }
}
