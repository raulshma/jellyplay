package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.datastore.reader.PerBookAppearance
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.core.datastore.reader.ReaderSlice
import com.raulshma.jellyplay.core.datastore.reader.ReaderStore
import com.raulshma.jellyplay.core.datastore.reader.ReaderTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers

/**
 * Pins [ReaderPreferences] at its interface (the snapshot + the commands):
 * the write-through semantics that keep rapid commands from dropping (the
 * bug class the VM's hand-rolled pending latches used to patch), the
 * in-flight guard that keeps a first-persist store emission from clobbering
 * a newer command, per-book routing, the override switch choreography, the
 * band clamps and the direction pin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPreferencesTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var readerStore: ReaderStore
    private lateinit var preferences: ReaderPreferences
    private val readerSlice = MutableStateFlow(ReaderSlice())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        readerStore = mockk()
        every { readerStore.reader } returns readerSlice
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
        coEvery { readerStore.setTocRailVisible(any()) } returns Unit
        coEvery { readerStore.setReadingSpeedWpm(any()) } returns Unit
        coEvery { readerStore.setAutoScrollSpeedPxPerSec(any()) } returns Unit
        coEvery { readerStore.setSpeechRate(any()) } returns Unit
        coEvery { readerStore.setSpeechPitch(any()) } returns Unit
        coEvery { readerStore.setReadingDirection(any(), any()) } returns Unit
        every { readerStore.lastCfi(any()) } returns null
        preferences = ReaderPreferences(
            store = readerStore,
            scope = CoroutineScope(StandardTestDispatcher(mainDispatcher.scheduler)),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid font-size taps accumulate through the synchronous snapshot`() = runTest(mainDispatcher) {
        preferences.attach("item-1")
        assertEquals(ReaderStore.DEFAULT_FONT_SIZE_PX, preferences.snapshot.value.effective.fontSizePx)

        // Three taps with no DataStore round trip between them: each folds
        // onto the snapshot the previous one wrote.
        preferences.adjustFontSize(+1)
        preferences.adjustFontSize(+1)
        preferences.adjustFontSize(+1)
        advanceUntilIdle()

        assertEquals(ReaderStore.DEFAULT_FONT_SIZE_PX + 3, preferences.snapshot.value.effective.fontSizePx)
        assertEquals(
            ReaderStore.DEFAULT_FONT_SIZE_PX + 3,
            preferences.snapshot.value.global.readerFontSizePx,
        )
    }

    @Test
    fun `a store emission landing inside the in-flight window does not clobber a newer command`() =
        runTest(mainDispatcher) {
            preferences.attach("item-1")

            preferences.setTheme(ReaderTheme.SEPIA)
            // The store's emission for the FIRST persist arrives before the
            // second command's persist has landed — the collector must not
            // adopt it over the SEPIA... LIGHT command below.
            readerSlice.value = ReaderSlice(readerTheme = ReaderTheme.SEPIA)
            preferences.setTheme(ReaderTheme.LIGHT)
            advanceUntilIdle()

            assertEquals(ReaderTheme.LIGHT, preferences.snapshot.value.global.readerTheme)

            // Once the persists have landed, external changes adopt again.
            readerSlice.value = ReaderSlice(readerTheme = ReaderTheme.DARK)
            advanceUntilIdle()
            assertEquals(ReaderTheme.DARK, preferences.snapshot.value.global.readerTheme)
        }

    @Test
    fun `theme and font-size writes route into the override when one is active`() = runTest(mainDispatcher) {
        readerSlice.value = ReaderSlice(
            readerTheme = ReaderTheme.DARK,
            readerFontSizePx = 17,
            perBookAppearance = mapOf("item-1" to PerBookAppearance(theme = ReaderTheme.DARK, fontSizePx = 19)),
        )
        preferences.attach("item-1")

        preferences.setTheme(ReaderTheme.SEPIA)
        preferences.adjustFontSize(+2)
        advanceUntilIdle()

        assertEquals(ReaderTheme.SEPIA, preferences.snapshot.value.effective.theme)
        assertEquals(21, preferences.snapshot.value.effective.fontSizePx)
        coVerify { readerStore.setPerBookAppearance("item-1", PerBookAppearance(ReaderTheme.SEPIA, 21)) }
        coVerify(exactly = 0) { readerStore.setReaderTheme(any()) }
        coVerify(exactly = 0) { readerStore.setReaderFontSizePx(any()) }
    }

    @Test
    fun `writes go global without an override`() = runTest(mainDispatcher) {
        preferences.attach("item-1")

        preferences.setTheme(ReaderTheme.LIGHT)
        preferences.adjustFontSize(+1)
        advanceUntilIdle()

        coVerify { readerStore.setReaderTheme(ReaderTheme.LIGHT) }
        coVerify { readerStore.setReaderFontSizePx(ReaderStore.DEFAULT_FONT_SIZE_PX + 1) }
        coVerify(exactly = 0) { readerStore.setPerBookAppearance(any(), any()) }
    }

    @Test
    fun `switching per-book off syncs the effective values into the globals then clears`() =
        runTest(mainDispatcher) {
            readerSlice.value = ReaderSlice(
                readerTheme = ReaderTheme.DARK,
                readerFontSizePx = 17,
                perBookAppearance = mapOf("item-1" to PerBookAppearance(ReaderTheme.SEPIA, 22)),
            )
            preferences.attach("item-1")

            preferences.setUsePerBookAppearance(false)
            advanceUntilIdle()

            coVerify { readerStore.setReaderTheme(ReaderTheme.SEPIA) }
            coVerify { readerStore.setReaderFontSizePx(22) }
            coVerify { readerStore.setPerBookAppearance("item-1", null) }
            assertNull(preferences.snapshot.value.perBook)
            // The look does not move: effective stays what the override had.
            assertEquals(ReaderTheme.SEPIA, preferences.snapshot.value.effective.theme)
            assertEquals(22, preferences.snapshot.value.effective.fontSizePx)
        }

    @Test
    fun `switching per-book on seeds the override with the effective values`() = runTest(mainDispatcher) {
        readerSlice.value = ReaderSlice(readerTheme = ReaderTheme.LIGHT, readerFontSizePx = 20)
        preferences.attach("item-1")

        preferences.setUsePerBookAppearance(true)
        advanceUntilIdle()

        coVerify {
            readerStore.setPerBookAppearance("item-1", PerBookAppearance(ReaderTheme.LIGHT, 20))
        }
        coVerify(exactly = 0) { readerStore.setReaderTheme(any()) }
        assertTrue(preferences.snapshot.value.perBookActive)
    }

    @Test
    fun `commands clamp into the store bands`() = runTest(mainDispatcher) {
        preferences.attach("item-1")

        preferences.setBrightnessPct(120)
        preferences.setLineHeightPct(999)
        preferences.setMarginPct(-5)
        preferences.setSpeechRate(500)
        preferences.setReadingSpeedWpm(5)
        advanceUntilIdle()

        assertEquals(100, preferences.snapshot.value.global.brightnessPct)
        assertEquals(200, preferences.snapshot.value.global.lineHeightPct)
        assertEquals(0, preferences.snapshot.value.global.marginPct)
        assertEquals(200, preferences.snapshot.value.global.speechRate)
        assertEquals(100, preferences.snapshot.value.global.readingSpeedWpm)
    }

    @Test
    fun `direction command pins the item choice and persists it`() = runTest(mainDispatcher) {
        preferences.attach("item-1")
        assertFalse(preferences.snapshot.value.directionPinned)

        preferences.setReadingDirection(ReadingDirection.RTL)
        advanceUntilIdle()

        assertEquals(ReadingDirection.RTL, preferences.snapshot.value.direction)
        assertTrue(preferences.snapshot.value.directionPinned)
        coVerify { readerStore.setReadingDirection("item-1", ReadingDirection.RTL) }
    }

    @Test
    fun `attach re-points routing and clears the previous item's optimistic per-book state`() =
        runTest(mainDispatcher) {
            preferences.attach("item-1")
            preferences.setUsePerBookAppearance(true) // seeds an override optimistically
            assertTrue(preferences.snapshot.value.perBookActive)

            preferences.attach("item-2")

            assertFalse(preferences.snapshot.value.perBookActive)
            assertNull(preferences.snapshot.value.perBook)
        }

    @Test
    fun `last-cfi rides through to the store`() = runTest(mainDispatcher) {
        every { readerStore.lastCfi("item-1") } returns "epubcfi(/6/8)"
        preferences.attach("item-1")

        assertEquals("epubcfi(/6/8)", preferences.lastCfi("item-1"))
        preferences.setLastCfi("item-1", "epubcfi(/6/10)")
        advanceUntilIdle()
        coVerify { readerStore.setLastCfi("item-1", "epubcfi(/6/10)") }
    }

    @Test
    fun `applyTypography writes only the changed axes`() = runTest(mainDispatcher) {
        val current = preferences.snapshot.value.typographyState()
        preferences.applyTypography(current.copy(justify = !current.justify))
        advanceUntilIdle()

        assertEquals(!current.justify, preferences.snapshot.value.global.justify)
        coVerify(exactly = 1) { readerStore.setJustify(!current.justify) }
        coVerify(exactly = 0) { readerStore.setFontFamily(any()) }
        coVerify(exactly = 0) { readerStore.setLineHeightPct(any()) }
        coVerify(exactly = 0) { readerStore.setMarginPct(any()) }
        coVerify(exactly = 0) { readerStore.setScrollMode(any()) }
    }

    @Test
    fun `applyBehavior writes only the changed axes`() = runTest(mainDispatcher) {
        val current = preferences.snapshot.value.behaviorState()
        preferences.applyBehavior(current.copy(readingSpeedWpm = current.readingSpeedWpm + 25))
        advanceUntilIdle()

        assertEquals(
            current.readingSpeedWpm + 25,
            preferences.snapshot.value.global.readingSpeedWpm,
        )
        coVerify(exactly = 1) { readerStore.setReadingSpeedWpm(current.readingSpeedWpm + 25) }
        coVerify(exactly = 0) { readerStore.setVolumeKeyPaging(any()) }
        coVerify(exactly = 0) { readerStore.setAnimatedPageTurns(any()) }
        coVerify(exactly = 0) { readerStore.setTocRailVisible(any()) }
    }
}
