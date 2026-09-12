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
 * round-trip, multi-book map coexistence, corrupt-blob degradation, and
 * [ReaderStore.clearAll].
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
    fun `clearAll removes every preference`() = runTest {
        store.setReadingDirection("book-1", ReadingDirection.RTL)
        store.setReadingDirection("book-2", ReadingDirection.RTL)
        store.setReaderTheme(ReaderTheme.LIGHT)
        store.setReaderFontSizePx(28)

        store.clearAll()

        assertTrue(slice().readingDirections.isEmpty())
        assertEquals(ReaderTheme.DARK, slice().readerTheme)
        assertEquals(ReaderStore.DEFAULT_FONT_SIZE_PX, slice().readerFontSizePx)
        assertEquals(
            listOf("reader_reading_directions", "reader_theme", "reader_font_size_px"),
            store.resetKeys.map { it.name },
        )
    }
}
