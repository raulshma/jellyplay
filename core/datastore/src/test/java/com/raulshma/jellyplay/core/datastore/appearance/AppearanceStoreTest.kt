package com.raulshma.jellyplay.core.datastore.appearance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ColorBlindMode
import com.raulshma.jellyplay.core.model.ColorStyle
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.HandMode
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the appearance store, focusing on the 3-way synthwave/soothing/
 * monochrome mutex that previously lived inline with no unit coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppearanceStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: AppearanceStore

    @Before
    fun setup() {
        runBlocking {
            val dataStore = TestDataStoreProvider.get(
                ApplicationProvider.getApplicationContext<android.content.Context>()
            )
            dataStore.edit { it.clear() }
            store = AppearanceStore(dataStore, scope)
            store.appearance.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.appearance.first()
        assertTrue(slice.dynamicTheming)
        assertEquals(ThemeMode.SYSTEM, slice.themeMode)
        assertEquals(ColorStyle.TONAL_SPOT, slice.colorStyle)
        assertFalse(slice.synthwaveMode)
        assertFalse(slice.soothingMode)
        assertFalse(slice.monochromeMode)
    }

    @Test
    fun `setSynthwaveMode clears soothing and monochrome`() = runTest {
        store.setSoothingMode(true)
        store.setMonochromeMode(true)
        store.setSynthwaveMode(true)
        val slice = store.appearance.first()
        assertTrue(slice.synthwaveMode)
        assertFalse(slice.soothingMode)
        assertFalse(slice.monochromeMode)
    }

    @Test
    fun `setSoothingMode clears synthwave and monochrome`() = runTest {
        store.setSynthwaveMode(true)
        store.setMonochromeMode(true)
        store.setSoothingMode(true)
        val slice = store.appearance.first()
        assertTrue(slice.soothingMode)
        assertFalse(slice.synthwaveMode)
        assertFalse(slice.monochromeMode)
    }

    @Test
    fun `setMonochromeMode clears synthwave and soothing`() = runTest {
        store.setSynthwaveMode(true)
        store.setSoothingMode(true)
        store.setMonochromeMode(true)
        val slice = store.appearance.first()
        assertTrue(slice.monochromeMode)
        assertFalse(slice.synthwaveMode)
        assertFalse(slice.soothingMode)
    }

    @Test
    fun `disabling one accent theme does not enable the others`() = runTest {
        store.setSynthwaveMode(true)
        store.setSynthwaveMode(false)
        val slice = store.appearance.first()
        assertFalse(slice.synthwaveMode)
        assertFalse(slice.soothingMode)
        assertFalse(slice.monochromeMode)
    }

    @Test
    fun `setThemeMode round-trips`() = runTest {
        store.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.appearance.first().themeMode)
    }

    @Test
    fun `restore(slice) round-trips a fully-populated slice`() = runTest {
        val slice = AppearanceSlice(
            dynamicTheming = false,
            themeMode = ThemeMode.DARK,
            contrastLevel = ContrastLevel.HIGH,
            oledMode = true,
            performanceMode = true,
            accentColorSwatch = "violet",
            colorStyle = ColorStyle.VIBRANT,
            synthwaveMode = true,
            synthwaveAccent = "cyan",
            soothingMode = false,
            soothingAccent = "forest",
            monochromeMode = false,
            showAdvancedSettings = true,
            reduceMotionEnabled = true,
            blueLightFilterEnabled = true,
            blueLightFilterStrength = 0.6f,
            backdropThemeMusicEnabled = true,
            hapticsEnabled = false,
            dateFormatPreference = DateFormatPreference.ISO,
            appFontScale = AppFontScale.LARGE,
            scheduledThemeStartHour = 20,
            scheduledThemeEndHour = 6,
            colorBlindMode = ColorBlindMode.DEUTERANOPIA,
            handMode = HandMode.LEFT,
        )

        store.restore(slice)

        assertEquals(slice, store.appearance.first())
    }
}
