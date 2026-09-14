package com.raulshma.jellyplay.core.datastore.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the reader preference store against the shared `"user_prefs"` test
 * DataStore: the LTR default for unvisited books, the per-book RTL override
 * round-trip, multi-book map coexistence, corrupt-blob degradation, the
 * Wave-1 appearance / pacing scalars (defaults, clamping, enum degradation)
 * and both new per-book JSON maps, and [ReaderStore.clearAll].
 *
 * Value assertions go through the pure [ReaderStore.read] projection over a
 * directly-read snapshot (deterministic), mirroring the HomeDiscoveryStoreTest
 * pattern; one bounded-wait test covers the reactive `reader` StateFlow the
 * [ReaderStore.readingDirection] convenience read observes.
 */
class ReaderStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: ReaderStore

    @BeforeTest
    fun setup() = runTest {
        // The test JVM shares one DataStore file across suites; start clean.
        dataStore = TestDataStoreProvider.get()
        dataStore.edit { it.clear() }
        store = ReaderStore(dataStore, scope)
        // Drain the Eagerly-cached slice so the cleared state is observed
        // before each test writes + reads.
        store.reader.first()
    }

    @AfterTest
    fun tearDown() = runTest {
        dataStore.edit { it.clear() }
        // Stop this instance's Eagerly sharing so it does not keep reacting to
        // later tests' writes on the shared DataStore singleton.
        scope.cancel()
    }

    /** Deterministic slice read: pure projection over the committed snapshot. */
    private suspend fun slice(): ReaderSlice = store.read(dataStore.data.first())

    @Test
    fun `defaults when empty`() = runTest {
        assertTrue(slice().readingDirections.isEmpty())
        assertEquals(ReadingDirection.LTR, store.readingDirection("book-1"))
        assertEquals(ReaderTheme.DARK, slice().readerTheme)
        assertEquals(ReaderStore.DEFAULT_FONT_SIZE_PX, slice().readerFontSizePx)
        assertEquals(ReaderFontFamily.SYSTEM, slice().fontFamily)
        assertEquals(ReaderStore.DEFAULT_LINE_HEIGHT_PCT, slice().lineHeightPct)
        assertEquals(ReaderStore.DEFAULT_MARGIN_PCT, slice().marginPct)
        assertEquals(false, slice().justify)
        assertEquals(false, slice().scrollMode)
        assertEquals(ReaderStore.DEFAULT_BRIGHTNESS_PCT, slice().brightnessPct)
        assertEquals(false, slice().volumeKeyPaging)
        assertEquals(true, slice().animatedPageTurns)
        assertEquals(false, slice().tocRailVisible)
        assertEquals(ReaderStore.DEFAULT_SPEECH_RATE, slice().speechRate)
        assertEquals(ReaderStore.DEFAULT_SPEECH_PITCH, slice().speechPitch)
        assertEquals(ReaderStore.DEFAULT_READING_SPEED_WPM, slice().readingSpeedWpm)
        assertTrue(slice().perBookAppearance.isEmpty())
        assertTrue(slice().lastCfis.isEmpty())
        assertEquals(null, store.lastCfi("book-1"))
        assertEquals(null, store.perBookAppearance("book-1"))
    }

    @Test
    fun `reader theme round-trips`() = runTest {
        store.setReaderTheme(ReaderTheme.SEPIA)
        assertEquals(ReaderTheme.SEPIA, slice().readerTheme)

        store.setReaderTheme(ReaderTheme.LIGHT)
        assertEquals(ReaderTheme.LIGHT, slice().readerTheme)
    }

    @Test
    fun `reader theme corrupt value degrades to dark`() = runTest {
        dataStore.edit { it[ReaderStore.Keys.READER_THEME] = "not-a-theme" }
        assertEquals(ReaderTheme.DARK, slice().readerTheme)
    }

    @Test
    fun `font size round-trips and clamps into the band`() = runTest {
        store.setReaderFontSizePx(24)
        assertEquals(24, slice().readerFontSizePx)

        store.setReaderFontSizePx(ReaderStore.MIN_FONT_SIZE_PX - 5)
        assertEquals(ReaderStore.MIN_FONT_SIZE_PX, slice().readerFontSizePx)

        store.setReaderFontSizePx(ReaderStore.MAX_FONT_SIZE_PX + 5)
        assertEquals(ReaderStore.MAX_FONT_SIZE_PX, slice().readerFontSizePx)
    }

    @Test
    fun `font family round-trips and a corrupt value degrades to system`() = runTest {
        store.setFontFamily(ReaderFontFamily.SERIF)
        assertEquals(ReaderFontFamily.SERIF, slice().fontFamily)

        dataStore.edit { it[ReaderStore.Keys.READER_FONT_FAMILY] = "not-a-family" }
        assertEquals(ReaderFontFamily.SYSTEM, slice().fontFamily)
    }

    @Test
    fun `pct and wpm scalars round-trip and clamp into their bands`() = runTest {
        store.setLineHeightPct(180)
        assertEquals(180, slice().lineHeightPct)
        store.setLineHeightPct(ReaderStore.MIN_LINE_HEIGHT_PCT - 1)
        assertEquals(ReaderStore.MIN_LINE_HEIGHT_PCT, slice().lineHeightPct)
        store.setLineHeightPct(ReaderStore.MAX_LINE_HEIGHT_PCT + 1)
        assertEquals(ReaderStore.MAX_LINE_HEIGHT_PCT, slice().lineHeightPct)

        store.setMarginPct(20)
        assertEquals(20, slice().marginPct)
        store.setMarginPct(-1)
        assertEquals(0, slice().marginPct)
        store.setMarginPct(101)
        assertEquals(100, slice().marginPct)

        store.setBrightnessPct(30)
        assertEquals(30, slice().brightnessPct)
        store.setBrightnessPct(-1)
        assertEquals(0, slice().brightnessPct)
        store.setBrightnessPct(101)
        assertEquals(100, slice().brightnessPct)

        store.setSpeechRate(150)
        assertEquals(150, slice().speechRate)
        store.setSpeechRate(ReaderStore.MIN_SPEECH_RATE - 1)
        assertEquals(ReaderStore.MIN_SPEECH_RATE, slice().speechRate)
        store.setSpeechRate(ReaderStore.MAX_SPEECH_RATE + 1)
        assertEquals(ReaderStore.MAX_SPEECH_RATE, slice().speechRate)

        store.setSpeechPitch(75)
        assertEquals(75, slice().speechPitch)
        store.setSpeechPitch(ReaderStore.MIN_SPEECH_PITCH - 1)
        assertEquals(ReaderStore.MIN_SPEECH_PITCH, slice().speechPitch)
        store.setSpeechPitch(ReaderStore.MAX_SPEECH_PITCH + 1)
        assertEquals(ReaderStore.MAX_SPEECH_PITCH, slice().speechPitch)

        store.setReadingSpeedWpm(300)
        assertEquals(300, slice().readingSpeedWpm)
        store.setReadingSpeedWpm(ReaderStore.MIN_READING_SPEED_WPM - 1)
        assertEquals(ReaderStore.MIN_READING_SPEED_WPM, slice().readingSpeedWpm)
        store.setReadingSpeedWpm(ReaderStore.MAX_READING_SPEED_WPM + 1)
        assertEquals(ReaderStore.MAX_READING_SPEED_WPM, slice().readingSpeedWpm)

        store.setAutoScrollSpeedPxPerSec(60)
        assertEquals(60, slice().autoScrollSpeedPxPerSec)
        store.setAutoScrollSpeedPxPerSec(ReaderStore.MIN_AUTO_SCROLL_SPEED_PX_PER_SEC - 1)
        assertEquals(ReaderStore.MIN_AUTO_SCROLL_SPEED_PX_PER_SEC, slice().autoScrollSpeedPxPerSec)
        store.setAutoScrollSpeedPxPerSec(ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC + 1)
        assertEquals(ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC, slice().autoScrollSpeedPxPerSec)
    }

    @Test
    fun `stored out-of-band ints are clamped on decode`() = runTest {
        // Hand-edited / legacy rows: the decode path defends independently of
        // the setters, so a raw write outside the band reads back clamped.
        dataStore.edit {
            it[ReaderStore.Keys.READER_LINE_HEIGHT_PCT] = 40
            it[ReaderStore.Keys.READER_MARGIN_PCT] = 500
            it[ReaderStore.Keys.READER_BRIGHTNESS_PCT] = -20
            it[ReaderStore.Keys.READER_SPEECH_RATE] = 999
            it[ReaderStore.Keys.READER_SPEECH_PITCH] = 1
            it[ReaderStore.Keys.READER_READING_SPEED_WPM] = 10_000
            it[ReaderStore.Keys.READER_AUTO_SCROLL_SPEED_PX] = 5_000
        }

        val slice = slice()
        assertEquals(ReaderStore.MIN_LINE_HEIGHT_PCT, slice.lineHeightPct)
        assertEquals(ReaderStore.MAX_MARGIN_PCT, slice.marginPct)
        assertEquals(0, slice.brightnessPct)
        assertEquals(ReaderStore.MAX_SPEECH_RATE, slice.speechRate)
        assertEquals(ReaderStore.MIN_SPEECH_PITCH, slice.speechPitch)
        assertEquals(ReaderStore.MAX_READING_SPEED_WPM, slice.readingSpeedWpm)
        assertEquals(ReaderStore.MAX_AUTO_SCROLL_SPEED_PX_PER_SEC, slice.autoScrollSpeedPxPerSec)
    }

    @Test
    fun `boolean toggles round-trip`() = runTest {
        store.setJustify(true)
        store.setScrollMode(true)
        store.setVolumeKeyPaging(true)
        store.setAnimatedPageTurns(false)
        store.setTocRailVisible(true)

        val slice = slice()
        assertEquals(true, slice.justify)
        assertEquals(true, slice.scrollMode)
        assertEquals(true, slice.volumeKeyPaging)
        assertEquals(false, slice.animatedPageTurns)
        assertEquals(true, slice.tocRailVisible)
    }

    @Test
    fun `setReadingDirection round-trips per item`() = runTest {
        store.setReadingDirection("book-1", ReadingDirection.RTL)
        assertEquals(ReadingDirection.RTL, slice().readingDirections["book-1"])

        store.setReadingDirection("book-1", ReadingDirection.LTR)
        assertEquals(ReadingDirection.LTR, slice().readingDirections["book-1"])
    }

    @Test
    fun `directions coexist across books`() = runTest {
        store.setReadingDirection("book-1", ReadingDirection.RTL)
        store.setReadingDirection("book-2", ReadingDirection.LTR)

        val directions = slice().readingDirections
        assertEquals(ReadingDirection.RTL, directions["book-1"])
        assertEquals(ReadingDirection.LTR, directions["book-2"])
    }

    @Test
    fun `readingDirection observes the committed state once the slice re-derives`() = runTest {
        store.setReadingDirection("book-1", ReadingDirection.RTL)

        // Bounded wait (same pattern as HomeDiscoveryStoreTest's reactive
        // test): the Eagerly StateFlow re-derives on the DataStore's emission,
        // not synchronously inside the setter.
        val observed = withTimeoutOrNull(5_000) {
            while (store.readingDirection("book-1") != ReadingDirection.RTL) {
                store.reader.first()
            }
            store.readingDirection("book-1")
        }
        assertEquals(ReadingDirection.RTL, observed)
        assertEquals(ReadingDirection.LTR, store.readingDirection("book-9"))
    }

    @Test
    fun `corrupt stored blob degrades to empty instead of throwing`() = runTest {
        dataStore.edit { it[ReaderStore.Keys.READING_DIRECTIONS] = "not json {" }

        assertTrue(slice().readingDirections.isEmpty())
        assertEquals(ReadingDirection.LTR, store.readingDirection("book-1"))
    }

    @Test
    fun `per-book appearance round-trips, clamps font size and coexists across books`() = runTest {
        store.setPerBookAppearance("book-1", PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 24))
        store.setPerBookAppearance("book-2", PerBookAppearance(theme = ReaderTheme.LIGHT))

        val appearances = slice().perBookAppearance
        assertEquals(PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 24), appearances["book-1"])
        assertEquals(PerBookAppearance(theme = ReaderTheme.LIGHT), appearances["book-2"])
        assertEquals(PerBookAppearance(theme = ReaderTheme.SEPIA, fontSizePx = 24), store.perBookAppearance("book-1"))

        // A raw out-of-band font size is clamped into the global band on write.
        store.setPerBookAppearance("book-3", PerBookAppearance(fontSizePx = 500))
        assertEquals(ReaderStore.MAX_FONT_SIZE_PX, slice().perBookAppearance["book-3"]!!.fontSizePx)

        // Clearing one book drops only its entry.
        store.setPerBookAppearance("book-1", null)
        assertTrue(!slice().perBookAppearance.containsKey("book-1"))
        assertEquals(ReaderTheme.LIGHT, slice().perBookAppearance["book-2"]!!.theme)
    }

    @Test
    fun `corrupt per-book appearance blob degrades to empty instead of throwing`() = runTest {
        dataStore.edit { it[ReaderStore.Keys.READER_PER_BOOK_APPEARANCE] = "not json {" }

        assertTrue(slice().perBookAppearance.isEmpty())
        assertEquals(null, store.perBookAppearance("book-1"))
    }

    @Test
    fun `lastCfi round-trips per item and clearLastCfi drops only that item`() = runTest {
        store.setLastCfi("book-1", "epubcfi(/6/4!/4/10,/1:20,/1:40)")
        store.setLastCfi("book-2", "epubcfi(/6/4!/4/14,/2:0,/2:8)")

        val cfis = slice().lastCfis
        assertEquals("epubcfi(/6/4!/4/10,/1:20,/1:40)", cfis["book-1"])
        assertEquals("epubcfi(/6/4!/4/10,/1:20,/1:40)", store.lastCfi("book-1"))
        assertEquals("epubcfi(/6/4!/4/14,/2:0,/2:8)", cfis["book-2"])

        store.clearLastCfi("book-1")

        assertTrue(!slice().lastCfis.containsKey("book-1"))
        assertEquals("epubcfi(/6/4!/4/14,/2:0,/2:8)", store.lastCfi("book-2"))
        assertEquals(null, store.lastCfi("book-1"))
    }

    @Test
    fun `corrupt lastCfi blob degrades to empty instead of throwing`() = runTest {
        dataStore.edit { it[ReaderStore.Keys.READER_LAST_CFIS] = "not json {" }

        assertTrue(slice().lastCfis.isEmpty())
        assertEquals(null, store.lastCfi("book-1"))
    }

    @Test
    fun `clearAll removes every preference`() = runTest {
        store.setReadingDirection("book-1", ReadingDirection.RTL)
        store.setReadingDirection("book-2", ReadingDirection.RTL)
        store.setReaderTheme(ReaderTheme.LIGHT)
        store.setReaderFontSizePx(28)
        store.setFontFamily(ReaderFontFamily.MONO)
        store.setLineHeightPct(140)
        store.setMarginPct(12)
        store.setJustify(true)
        store.setScrollMode(true)
        store.setBrightnessPct(40)
        store.setVolumeKeyPaging(true)
        store.setAnimatedPageTurns(false)
        store.setTocRailVisible(true)
        store.setSpeechRate(120)
        store.setSpeechPitch(90)
        store.setReadingSpeedWpm(320)
        store.setPerBookAppearance("book-1", PerBookAppearance(theme = ReaderTheme.SEPIA))
        store.setLastCfi("book-1", "epubcfi(...)")

        store.clearAll()

        val slice = slice()
        assertTrue(slice.readingDirections.isEmpty())
        assertEquals(ReaderTheme.DARK, slice.readerTheme)
        assertEquals(ReaderStore.DEFAULT_FONT_SIZE_PX, slice.readerFontSizePx)
        assertEquals(ReaderFontFamily.SYSTEM, slice.fontFamily)
        assertEquals(ReaderStore.DEFAULT_LINE_HEIGHT_PCT, slice.lineHeightPct)
        assertEquals(ReaderStore.DEFAULT_MARGIN_PCT, slice.marginPct)
        assertEquals(false, slice.justify)
        assertEquals(false, slice.scrollMode)
        assertEquals(ReaderStore.DEFAULT_BRIGHTNESS_PCT, slice.brightnessPct)
        assertEquals(false, slice.volumeKeyPaging)
        assertEquals(true, slice.animatedPageTurns)
        assertEquals(false, slice.tocRailVisible)
        assertEquals(ReaderStore.DEFAULT_SPEECH_RATE, slice.speechRate)
        assertEquals(ReaderStore.DEFAULT_SPEECH_PITCH, slice.speechPitch)
        assertEquals(ReaderStore.DEFAULT_READING_SPEED_WPM, slice.readingSpeedWpm)
        assertEquals(ReaderStore.DEFAULT_AUTO_SCROLL_SPEED_PX_PER_SEC, slice.autoScrollSpeedPxPerSec)
        assertTrue(slice.perBookAppearance.isEmpty())
        assertTrue(slice.lastCfis.isEmpty())
        assertEquals(
            listOf(
                "reader_reading_directions",
                "reader_theme",
                "reader_font_size_px",
                "reader_font_family",
                "reader_line_height_pct",
                "reader_margin_pct",
                "reader_justify",
                "reader_scroll_mode",
                "reader_brightness_pct",
                "reader_volume_key_paging",
                "reader_animated_page_turns",
                "reader_toc_rail",
                "reader_speech_rate",
                "reader_speech_pitch",
                "reader_reading_speed_wpm",
                "reader_auto_scroll_speed_px",
                "reader_per_book_appearance",
                "reader_last_cfis",
            ),
            store.resetKeys.map { it.name },
        )
    }
}
