package com.raulshma.jellyplay.core.datastore.appearance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the appearance store, focusing on the single `theme_variant` key
 * that selects the active theme style, the per-variant accent keys, and the
 * read-time derivation from the legacy synthwave/soothing/monochrome booleans.
 */
class AppearanceStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: AppearanceStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
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
        assertEquals("standard", slice.themeVariant)
        assertEquals("punch", slice.vividAccent)
        assertEquals("emerald", slice.auroraAccent)
        assertEquals("rose", slice.sakuraAccent)
        assertEquals("cobalt", slice.vectorPopAccent)
    }

    @Test
    fun `setThemeVariant round-trips every variant`() = runTest {
        for (variant in listOf("standard", "synthwave", "soothing", "monochrome", "vivid", "aurora", "sakura", "vector_pop")) {
            store.setThemeVariant(variant)
            assertEquals(variant, store.appearance.first().themeVariant)
        }
    }

    @Test
    fun `legacy synthwave boolean derives theme_variant`() = runTest {
        dataStore.edit { it[AppearanceStore.Keys.SYNTHWAVE_MODE] = true }
        assertEquals("synthwave", store.appearance.first().themeVariant)
    }

    @Test
    fun `legacy soothing boolean derives theme_variant`() = runTest {
        dataStore.edit { it[AppearanceStore.Keys.SOOTHING_MODE] = true }
        assertEquals("soothing", store.appearance.first().themeVariant)
    }

    @Test
    fun `legacy monochrome boolean derives theme_variant`() = runTest {
        dataStore.edit { it[AppearanceStore.Keys.MONOCHROME_MODE] = true }
        assertEquals("monochrome", store.appearance.first().themeVariant)
    }

    @Test
    fun `explicit theme_variant wins over legacy booleans`() = runTest {
        dataStore.edit { prefs ->
            prefs[AppearanceStore.Keys.SYNTHWAVE_MODE] = true
            prefs[AppearanceStore.Keys.THEME_VARIANT] = "aurora"
        }
        assertEquals("aurora", store.appearance.first().themeVariant)
    }

    @Test
    fun `restore normalizes mixed-case theme_variant`() = runTest {
        val slice = AppearanceSlice(themeVariant = "Sakura")
        store.restore(slice)
        assertEquals("sakura", store.appearance.first().themeVariant)
    }

    @Test
    fun `setVariantAccent writes the right per-variant key`() = runTest {
        store.setVariantAccent("vivid", "tangerine")
        store.setVariantAccent("aurora", "violet")
        store.setVariantAccent("sakura", "mint")
        store.setVariantAccent("vector_pop", "tomato")
        store.setVariantAccent("synthwave", "cyan")
        store.setVariantAccent("soothing", "sage")
        val slice = store.appearance.first()
        assertEquals("tangerine", slice.vividAccent)
        assertEquals("violet", slice.auroraAccent)
        assertEquals("mint", slice.sakuraAccent)
        assertEquals("tomato", slice.vectorPopAccent)
        assertEquals("cyan", slice.synthwaveAccent)
        assertEquals("sage", slice.soothingAccent)
    }

    @Test
    fun `setVariantAccent ignores unknown variants`() = runTest {
        store.setVariantAccent("standard", "ignored")
        store.setVariantAccent("nonsense", "ignored")
        val slice = store.appearance.first()
        assertEquals("punch", slice.vividAccent)
        assertEquals("magenta", slice.synthwaveAccent)
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
            themeVariant = "sakura",
            synthwaveAccent = "cyan",
            soothingAccent = "forest",
            vividAccent = "lime",
            auroraAccent = "ice",
            sakuraAccent = "peach",
            vectorPopAccent = "kelly",
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
