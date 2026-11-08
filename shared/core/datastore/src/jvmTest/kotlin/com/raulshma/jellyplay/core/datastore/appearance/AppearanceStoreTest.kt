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
import com.raulshma.jellyplay.core.model.ImageDecodeCap
import com.raulshma.jellyplay.core.model.LayoutMode
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PerformanceOverride
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.TvOverscan
import com.raulshma.jellyplay.core.model.TvUiScale
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
        // The performance posture defaults to today's mode-OFF semantics.
        assertEquals(PerformanceLevel.STANDARD, slice.performanceLevel)
        assertFalse(slice.performanceMode)
        assertEquals(PerformanceOverride.AUTO, slice.perfAnimations)
        assertEquals(PerformanceOverride.AUTO, slice.perfSharedElements)
        assertEquals(PerformanceOverride.AUTO, slice.perfNavTransitions)
        assertEquals(ImageDecodeCap.AUTO, slice.perfImageDecodeCap)
        assertEquals(PerformanceOverride.AUTO, slice.perfImageCrossfade)
        assertEquals(PerformanceOverride.AUTO, slice.perfBlurhash)
        assertEquals(PerformanceOverride.AUTO, slice.perfQualityBadges)
        assertEquals(PerformanceOverride.AUTO, slice.perfAmbientHeroVideo)
        assertEquals(PerformanceOverride.AUTO, slice.perfBackdropBlur)
        assertEquals(PerformanceOverride.AUTO, slice.perfScreensaverMotion)
        assertEquals(0, slice.imageMemoryCachePercent)
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
    fun `setLayoutMode round-trips and defaults to AUTO`() = runTest {
        assertEquals(LayoutMode.AUTO, store.appearance.first().layoutMode)
        store.setLayoutMode(LayoutMode.TABLET)
        assertEquals(LayoutMode.TABLET, store.appearance.first().layoutMode)
        store.setLayoutMode(LayoutMode.PHONE)
        assertEquals(LayoutMode.PHONE, store.appearance.first().layoutMode)
    }

    @Test
    fun `setTvOverscan round-trips and defaults to the guideline 5 percent`() = runTest {
        assertEquals(TvOverscan.FIVE, store.appearance.first().tvOverscan)
        store.setTvOverscan(TvOverscan.OFF)
        assertEquals(TvOverscan.OFF, store.appearance.first().tvOverscan)
        store.setTvOverscan(TvOverscan.TEN)
        assertEquals(TvOverscan.TEN, store.appearance.first().tvOverscan)
    }

    @Test
    fun `setTvUiScale round-trips and defaults to the shipped 85 percent`() = runTest {
        assertEquals(TvUiScale.SMALL, store.appearance.first().tvUiScale)
        store.setTvUiScale(TvUiScale.DEFAULT)
        assertEquals(TvUiScale.DEFAULT, store.appearance.first().tvUiScale)
        store.setTvUiScale(TvUiScale.COMPACT)
        assertEquals(TvUiScale.COMPACT, store.appearance.first().tvUiScale)
    }

    @Test
    fun `corrupt layout_mode falls back to AUTO`() = runTest {
        store.setLayoutMode(LayoutMode.TABLET)
        dataStore.edit {
            it[AppearanceStore.Keys.LAYOUT_MODE] = "wide"
        }
        assertEquals(LayoutMode.AUTO, store.appearance.first().layoutMode)
    }

    @Test
    fun `corrupt enum values fall back to defaults, siblings keep real values`() = runTest {
        store.setHapticsEnabled(false)
        dataStore.edit {
            it[AppearanceStore.Keys.THEME_MODE] = "nonsense"
            // Legacy lowercase casing matches no enum name either.
            it[AppearanceStore.Keys.HAND_MODE] = "left"
        }
        val slice = store.appearance.first()
        assertEquals(ThemeMode.SYSTEM, slice.themeMode)
        assertEquals(HandMode.RIGHT, slice.handMode)
        assertEquals(false, slice.hapticsEnabled)
    }

    // ------------------------------------------------------------------
    // The performance master: legacy fallback, the pairing write, and the
    // granular rows' write-throughs.
    // ------------------------------------------------------------------

    @Test
    fun `legacy performance_mode boolean alone derives the level`() = runTest {
        dataStore.edit { it[AppearanceStore.Keys.PERFORMANCE_MODE] = true }
        val slice = store.appearance.first()
        assertEquals(PerformanceLevel.PERFORMANCE, slice.performanceLevel)
        assertTrue(slice.performanceMode)
    }

    @Test
    fun `legacy performance_mode false reads STANDARD`() = runTest {
        dataStore.edit { it[AppearanceStore.Keys.PERFORMANCE_MODE] = false }
        val slice = store.appearance.first()
        assertEquals(PerformanceLevel.STANDARD, slice.performanceLevel)
        assertFalse(slice.performanceMode)
    }

    @Test
    fun `setPerformanceLevel mirrors the legacy boolean both ways`() = runTest {
        store.setPerformanceLevel(PerformanceLevel.BALANCED)
        var slice = store.appearance.first()
        assertEquals(PerformanceLevel.BALANCED, slice.performanceLevel)
        assertFalse(slice.performanceMode)
        assertFalse(dataStore.data.first()[AppearanceStore.Keys.PERFORMANCE_MODE] ?: true)

        store.setPerformanceLevel(PerformanceLevel.PERFORMANCE)
        slice = store.appearance.first()
        assertEquals(PerformanceLevel.PERFORMANCE, slice.performanceLevel)
        assertTrue(slice.performanceMode)
        assertTrue(dataStore.data.first()[AppearanceStore.Keys.PERFORMANCE_MODE] ?: false)

        store.setPerformanceLevel(PerformanceLevel.STANDARD)
        slice = store.appearance.first()
        assertEquals(PerformanceLevel.STANDARD, slice.performanceLevel)
        assertFalse(slice.performanceMode)
        assertFalse(dataStore.data.first()[AppearanceStore.Keys.PERFORMANCE_MODE] ?: true)
    }

    @Test
    fun `setPerformanceMode compat wrapper maps onto the level`() = runTest {
        store.setPerformanceMode(true)
        assertEquals(PerformanceLevel.PERFORMANCE, store.appearance.first().performanceLevel)
        store.setPerformanceMode(false)
        assertEquals(PerformanceLevel.STANDARD, store.appearance.first().performanceLevel)
    }

    @Test
    fun `writing BALANCED over a legacy-true install flips both keys`() = runTest {
        dataStore.edit { it[AppearanceStore.Keys.PERFORMANCE_MODE] = true }
        store.setPerformanceLevel(PerformanceLevel.BALANCED)
        val stored = dataStore.data.first()
        assertEquals("BALANCED", stored[AppearanceStore.Keys.PERFORMANCE_LEVEL])
        assertEquals(false, stored[AppearanceStore.Keys.PERFORMANCE_MODE] ?: true)
        assertEquals(PerformanceLevel.BALANCED, store.appearance.first().performanceLevel)
    }

    @Test
    fun `corrupt performance_level falls back to STANDARD`() = runTest {
        store.setPerformanceLevel(PerformanceLevel.PERFORMANCE)
        dataStore.edit { it[AppearanceStore.Keys.PERFORMANCE_LEVEL] = "nonsense" }
        val slice = store.appearance.first()
        assertEquals(PerformanceLevel.STANDARD, slice.performanceLevel)
        assertFalse(slice.performanceMode)
    }

    @Test
    fun `performance override rows round-trip`() = runTest {
        store.setPerfAnimations(PerformanceOverride.ON)
        store.setPerfSharedElements(PerformanceOverride.OFF)
        store.setPerfNavTransitions(PerformanceOverride.ON)
        store.setPerfImageDecodeCap(ImageDecodeCap.P720)
        store.setPerfImageCrossfade(PerformanceOverride.OFF)
        store.setPerfBlurhash(PerformanceOverride.ON)
        store.setPerfQualityBadges(PerformanceOverride.OFF)
        store.setPerfAmbientHeroVideo(PerformanceOverride.ON)
        store.setPerfBackdropBlur(PerformanceOverride.OFF)
        store.setPerfScreensaverMotion(PerformanceOverride.ON)
        val slice = store.appearance.first()
        assertEquals(PerformanceOverride.ON, slice.perfAnimations)
        assertEquals(PerformanceOverride.OFF, slice.perfSharedElements)
        assertEquals(PerformanceOverride.ON, slice.perfNavTransitions)
        assertEquals(ImageDecodeCap.P720, slice.perfImageDecodeCap)
        assertEquals(PerformanceOverride.OFF, slice.perfImageCrossfade)
        assertEquals(PerformanceOverride.ON, slice.perfBlurhash)
        assertEquals(PerformanceOverride.OFF, slice.perfQualityBadges)
        assertEquals(PerformanceOverride.ON, slice.perfAmbientHeroVideo)
        assertEquals(PerformanceOverride.OFF, slice.perfBackdropBlur)
        assertEquals(PerformanceOverride.ON, slice.perfScreensaverMotion)
    }

    @Test
    fun `corrupt override enum falls back to AUTO`() = runTest {
        store.setPerfAnimations(PerformanceOverride.ON)
        dataStore.edit { it[AppearanceStore.Keys.PERF_ANIMATIONS] = "MAYBE" }
        assertEquals(PerformanceOverride.AUTO, store.appearance.first().perfAnimations)
    }

    @Test
    fun `image memory cache percent clamps to the AUTO sentinel or the 5 to 35 band`() = runTest {
        // 0 = the AUTO sentinel, preserved as-is.
        store.setImageMemoryCachePercent(0)
        assertEquals(0, store.appearance.first().imageMemoryCachePercent)
        // In-band values pass through.
        store.setImageMemoryCachePercent(5)
        assertEquals(5, store.appearance.first().imageMemoryCachePercent)
        store.setImageMemoryCachePercent(20)
        assertEquals(20, store.appearance.first().imageMemoryCachePercent)
        store.setImageMemoryCachePercent(35)
        assertEquals(35, store.appearance.first().imageMemoryCachePercent)
        // Out-of-band values clamp into the band.
        store.setImageMemoryCachePercent(3)
        assertEquals(5, store.appearance.first().imageMemoryCachePercent)
        store.setImageMemoryCachePercent(40)
        assertEquals(35, store.appearance.first().imageMemoryCachePercent)
        store.setImageMemoryCachePercent(-7)
        assertEquals(5, store.appearance.first().imageMemoryCachePercent)
        // The AUTO sentinel is reachable again after clamping.
        store.setImageMemoryCachePercent(0)
        assertEquals(0, store.appearance.first().imageMemoryCachePercent)
    }

    @Test
    fun `restore maps an old-backup boolean-only slice onto the level`() = runTest {
        // OLD backup shape: performanceMode carried, performanceLevel missing
        // (kotlinx decodes it at its STANDARD default).
        store.restore(AppearanceSlice(performanceMode = true))
        var slice = store.appearance.first()
        assertEquals(PerformanceLevel.PERFORMANCE, slice.performanceLevel)
        assertTrue(slice.performanceMode)

        store.restore(AppearanceSlice(performanceMode = false))
        slice = store.appearance.first()
        assertEquals(PerformanceLevel.STANDARD, slice.performanceLevel)
        assertFalse(slice.performanceMode)
    }

    @Test
    fun `restore keeps an explicit new-backup level`() = runTest {
        store.restore(AppearanceSlice(performanceLevel = PerformanceLevel.BALANCED))
        val slice = store.appearance.first()
        assertEquals(PerformanceLevel.BALANCED, slice.performanceLevel)
        assertFalse(slice.performanceMode)
    }

    @Test
    fun `restore(slice) round-trips a fully-populated slice`() = runTest {
        val slice = AppearanceSlice(
            dynamicTheming = false,
            themeMode = ThemeMode.DARK,
            contrastLevel = ContrastLevel.HIGH,
            oledMode = true,
            performanceLevel = PerformanceLevel.PERFORMANCE,
            performanceMode = true,
            perfAnimations = PerformanceOverride.ON,
            perfSharedElements = PerformanceOverride.OFF,
            perfNavTransitions = PerformanceOverride.ON,
            perfImageDecodeCap = ImageDecodeCap.P512,
            perfImageCrossfade = PerformanceOverride.OFF,
            perfBlurhash = PerformanceOverride.ON,
            perfQualityBadges = PerformanceOverride.OFF,
            perfAmbientHeroVideo = PerformanceOverride.ON,
            perfBackdropBlur = PerformanceOverride.OFF,
            perfScreensaverMotion = PerformanceOverride.ON,
            imageMemoryCachePercent = 30,
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
            layoutMode = LayoutMode.PHONE,
            tvOverscan = TvOverscan.TEN,
        )

        store.restore(slice)

        assertEquals(slice, store.appearance.first())
    }
}
