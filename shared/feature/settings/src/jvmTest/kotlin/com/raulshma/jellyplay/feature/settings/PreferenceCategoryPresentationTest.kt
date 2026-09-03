package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the explicitly pure diff/presentation layer of the Factory Reset /
 * Import Preview screens (PreferenceCategoryPresentation.kt — the KDoc on
 * [appRuntimeFields] notes "this pure function does not need a @Composable
 * context", and the whole registry is plain data in → [PreferenceField] out).
 *
 * Invariants under test: the current-vs-factory diff (unchanged passes
 * through, single/multiple changes detected with the exact label), the
 * registry's coverage of [PreferenceResetCategory], the value formatting the
 * UI renders verbatim (On/Off, enum prettifying, seconds/percent), and the
 * AppRuntime extras diff used by import-preview's "everything" card.
 */
class PreferenceCategoryPresentationTest {

    private val factory = UserPreferences()

    private fun view(category: PreferenceResetCategory): PreferenceCategoryView =
        PreferenceCategoryViews.first { it.category == category }

    // ---------------------------------------------------------------- diff

    @Test
    fun `unchanged prefs produce no changed fields in any category`() {
        PreferenceCategoryViews.forEach { view ->
            assertTrue(
                view.changedFields(factory, factory).isEmpty(),
                "${view.category} must diff clean against the factory baseline",
            )
        }
    }

    @Test
    fun `single appearance change is detected with the exact label`() {
        val prefs = factory.copy(themeMode = ThemeMode.DARK)

        val changed = view(PreferenceResetCategory.APPEARANCE).changedFields(prefs, factory)

        assertEquals(listOf("Theme Mode"), changed.map { it.label })
        val field = changed.single()
        assertTrue(field.changed)
        assertEquals("Dark", field.currentValue, "plain enums prettify without a displayName")
        assertEquals("System", field.factoryValue)
    }

    @Test
    fun `multiple changes in one category are all detected`() {
        val prefs = factory.copy(themeMode = ThemeMode.DARK, oledMode = true)

        val changed = view(PreferenceResetCategory.APPEARANCE).changedFields(prefs, factory)

        assertEquals(setOf("Theme Mode", "OLED Mode"), changed.map { it.label }.toSet())
        assertEquals(2, changed.size)
    }

    @Test
    fun `security pin state is surfaced as changed fields`() {
        val prefs = factory.copy(pinLockEnabled = true, pinHash = "stored-hash")

        val changed = view(PreferenceResetCategory.SECURITY).changedFields(prefs, factory)

        assertEquals(setOf("PIN Lock", "PIN Set"), changed.map { it.label }.toSet())
    }

    @Test
    fun `every category surfaces fields and totals consistently`() {
        PreferenceCategoryViews.forEach { view ->
            val fields = view.fields(factory, factory)
            assertTrue(fields.isNotEmpty(), "${view.category} must surface user-facing fields")
            assertEquals(fields.size, view.totalFields(factory, factory))
            fields.forEach { field ->
                assertTrue(field.label.isNotBlank(), "blank label in ${view.category}")
                assertTrue(field.currentValue.isNotEmpty(), "blank current value for '${field.label}'")
                assertTrue(field.factoryValue.isNotEmpty(), "blank factory value for '${field.label}'")
                assertFalse(field.changed, "field '${field.label}' must diff clean against factory")
            }
        }
    }

    // ---------------------------------------------------------------- registry

    @Test
    fun `registry covers every reset category exactly once`() {
        assertEquals(
            PreferenceResetCategory.entries.toSet(),
            PreferenceCategoryViews.map { it.category }.toSet(),
            "the UI iterates the registry — a missing category is unreachable",
        )
        assertEquals(PreferenceCategoryViews.size, PreferenceResetCategory.entries.size)
    }

    // ---------------------------------------------------------------- formatting

    @Test
    fun `booleans render On and Off`() {
        val prefs = factory.copy(oledMode = true)

        val field = view(PreferenceResetCategory.APPEARANCE)
            .fields(prefs, factory)
            .first { it.label == "OLED Mode" }

        assertEquals("On", field.currentValue)
        assertEquals("Off", field.factoryValue)
    }

    @Test
    fun `enum fields with displayName resolve the localized-style name`() {
        // DecoderMode implements HasDisplayName → displayName wins over prettifying.
        val prefs = factory.copy(decoderMode = DecoderMode.SW_ONLY)

        val field = view(PreferenceResetCategory.PLAYBACK)
            .fields(prefs, factory)
            .first { it.label == "Decoder Mode" }

        assertEquals("Software Only", field.currentValue)
        assertEquals("Hardware (Preferred)", field.factoryValue)
    }

    @Test
    fun `durations render in seconds and strengths in percent`() {
        val field = view(PreferenceResetCategory.PLAYBACK)
            .fields(factory, factory)
            .first { it.label == "Seek Duration" }
        assertEquals("10.0s", field.currentValue, "default videoSeekDurationMs is 10_000")

        val blueLight = view(PreferenceResetCategory.APPEARANCE)
            .fields(factory, factory)
            .first { it.label == "Blue Light Strength" }
        assertEquals("30%", blueLight.currentValue, "default blueLightFilterStrength is 0.3f")
    }

    @Test
    fun `nullable strings fall back to System`() {
        val prefs = factory.copy(preferredSubtitleLanguage = "eng")

        val fields = view(PreferenceResetCategory.SUBTITLES_LANGUAGE).fields(prefs, factory)

        assertEquals("eng", fields.first { it.label == "Preferred Subtitle Language" }.currentValue)
        assertEquals("System", fields.first { it.label == "Preferred Subtitle Language" }.factoryValue)
        assertEquals("System", fields.first { it.label == "Preferred Audio Language" }.currentValue)
    }

    // ---------------------------------------------------------------- app runtime extras

    @Test
    fun `matching runtime states diff clean`() {
        val state = AppRuntimeState(
            favoriteChannels = setOf("ch-1"),
            liveTvLastChannelId = "chan-9",
            onboardingCompleted = true,
        )

        val fields = appRuntimeFields(current = state, incoming = state)

        assertTrue(fields.isNotEmpty())
        assertFalse(fields.any { it.changed }, "identical states must diff clean")
    }

    @Test
    fun `runtime diffs render the none label for empty values`() {
        val fields = appRuntimeFields(current = AppRuntimeState(), incoming = AppRuntimeState())

        // The nullable/collection fields fall back to the none label; the
        // onboarding flag is a boolean and renders On/Off instead.
        assertEquals("None", fields.first { it.label == "Favorite Channels" }.currentValue)
        assertEquals("None", fields.first { it.label == "Last Live-TV Channel" }.currentValue)
        assertEquals("None", fields.first { it.label == "Watch Later Playlist" }.currentValue)
        assertEquals("None", fields.first { it.label == "Watch Later Playlist" }.factoryValue)
        assertEquals("None", fields.first { it.label == "Recent DLNA Devices" }.currentValue)
        assertEquals("Off", fields.first { it.label == "Onboarding Completed" }.currentValue)
    }

    @Test
    fun `runtime changes are detected with sorted channel lists`() {
        val current = AppRuntimeState(favoriteChannels = setOf("ch-b", "ch-a"), onboardingCompleted = true)
        val incoming = AppRuntimeState(liveTvLastChannelId = "chan-1")

        val fields = appRuntimeFields(current = current, incoming = incoming)

        assertEquals("ch-a, ch-b", fields.first { it.label == "Favorite Channels" }.currentValue,
            "channels must be sorted for a stable diff")
        assertEquals("None", fields.first { it.label == "Favorite Channels" }.factoryValue)
        assertEquals("chan-1", fields.first { it.label == "Last Live-TV Channel" }.factoryValue)
        assertEquals("None", fields.first { it.label == "Last Live-TV Channel" }.currentValue)
        assertTrue(fields.first { it.label == "Onboarding Completed" }.changed)
        assertEquals("On", fields.first { it.label == "Onboarding Completed" }.currentValue)
        assertTrue(fields.any { it.changed })
    }

    @Test
    fun `dlna devices render as a count with the none label when empty`() {
        val fields = appRuntimeFields(
            current = AppRuntimeState(recentDlnaDevices = emptyList()),
            incoming = AppRuntimeState(recentDlnaDevices = listOf(dlna("a"), dlna("b"))),
        )

        assertEquals("None", fields.first { it.label == "Recent DLNA Devices" }.currentValue)
        assertEquals("2 devices", fields.first { it.label == "Recent DLNA Devices" }.factoryValue)
    }

    @Test
    fun `custom none label is honored`() {
        val fields = appRuntimeFields(
            current = AppRuntimeState(),
            incoming = AppRuntimeState(),
            noneLabel = "—",
        )

        assertEquals("—", fields.first { it.label == "Favorite Channels" }.currentValue)
        assertEquals("—", fields.first { it.label == "Last Live-TV Channel" }.currentValue)
        assertEquals("—", fields.first { it.label == "Watch Later Playlist" }.currentValue)
        assertEquals("—", fields.first { it.label == "Recent DLNA Devices" }.factoryValue)
    }

    private fun dlna(id: String): DlnaDeviceRef =
        DlnaDeviceRef(id = id, name = id, locationUrl = "http://$id")
}
