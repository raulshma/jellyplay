package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSliceSnapshot
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import org.jetbrains.compose.resources.StringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the explicitly pure diff/presentation layer of the Factory Reset /
 * Import Preview screens (PreferenceCategoryPresentation.kt). The rows are
 * DERIVED from the [PreferenceSliceSnapshot] slices with localized label
 * resources; the tests pin: the current-vs-baseline diff (unchanged passes
 * through, single/multiple changes detected with the right resource→label
 * binding), the registry's coverage of [PreferenceResetCategory], the value
 * formatting the UI renders verbatim (On/Off, enum prettifying,
 * seconds/percent), and the AppRuntime extras diff used by import-preview's
 * "everything" card.
 */
class PreferenceCategoryPresentationTest {

    private val factorySlices = PreferenceSliceSnapshot.FACTORY

    private fun snapshot(slices: PreferenceSliceSnapshot, labels: Map<StringResource, String> = emptyMap()): PreferenceDiffSnapshot =
        PreferenceDiffSnapshot(slices) { res -> labels[res] ?: res.toString() }

    private fun view(category: PreferenceResetCategory): PreferenceCategoryView =
        PreferenceCategoryViews.first { it.category == category }

    /** The changed rows of a diff, with the test-mapped label for readability. */
    private fun changedLabels(
        current: PreferenceDiffSnapshot,
        baseline: PreferenceDiffSnapshot,
        category: PreferenceResetCategory,
    ): List<String> = view(category).changedFields(current, baseline).map { it.label }

    // ---------------------------------------------------------------- diff

    @Test
    fun `unchanged prefs produce no changed fields in any category`() {
        val factory = snapshot(factorySlices)
        PreferenceCategoryViews.forEach { view ->
            assertTrue(
                view.changedFields(factory, factory).isEmpty(),
                "${view.category} must diff clean against the factory baseline",
            )
        }
    }

    @Test
    fun `single appearance change is detected with the exact label`() {
        val prefs = snapshot(
            factorySlices.copy(appearance = factorySlices.appearance.copy(themeMode = ThemeMode.DARK)),
            labels = mapOf(Res.string.ss_theme_mode_title to "Theme Mode"),
        )
        val factory = snapshot(factorySlices, labels = mapOf(Res.string.ss_theme_mode_title to "Theme Mode"))

        val changed = changedLabels(prefs, factory, PreferenceResetCategory.APPEARANCE)

        assertEquals(listOf("Theme Mode"), changed)
        val field = view(PreferenceResetCategory.APPEARANCE).changedFields(prefs, factory).single()
        assertTrue(field.changed)
        assertEquals("Dark", field.currentValue, "plain enums prettify without a displayName")
        assertEquals("System", field.factoryValue)
    }

    @Test
    fun `multiple changes in one category are all detected`() {
        val labels = mapOf(
            Res.string.ss_theme_mode_title to "Theme Mode",
            Res.string.ss_oled_mode_title to "OLED Mode",
        )
        val prefs = snapshot(
            factorySlices.copy(
                appearance = factorySlices.appearance.copy(themeMode = ThemeMode.DARK, oledMode = true),
            ),
            labels,
        )
        val factory = snapshot(factorySlices, labels)

        assertEquals(
            setOf("Theme Mode", "OLED Mode"),
            changedLabels(prefs, factory, PreferenceResetCategory.APPEARANCE).toSet(),
        )
    }

    @Test
    fun `security pin state is surfaced as changed fields`() {
        val labels = mapOf(
            Res.string.ss_pin_lock_title to "PIN Lock",
            Res.string.diff_pin_set to "PIN Set",
        )
        val prefs = snapshot(
            factorySlices.copy(security = factorySlices.security.copy(pinLockEnabled = true, pinHash = "stored-hash")),
            labels,
        )
        val factory = snapshot(factorySlices, labels)

        assertEquals(
            setOf("PIN Lock", "PIN Set"),
            changedLabels(prefs, factory, PreferenceResetCategory.SECURITY).toSet(),
        )
    }

    @Test
    fun `every category surfaces fields and totals consistently`() {
        val factory = snapshot(factorySlices)
        PreferenceCategoryViews.forEach { view ->
            assertTrue(
                view.changedFields(factory, factory).isEmpty(),
                "${view.category} must diff clean against factory",
            )
            assertEquals(view.diffFields.size, view.totalFields(factory, factory), "${view.category} totals")
            view.diffFields.forEach { field ->
                val value = field.value(factorySlices)
                assertTrue(value.isNotEmpty(), "blank current value for '${field.labelRes}' in ${view.category}")
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

    @Test
    fun `every row carries a distinct label resource within its category`() {
        PreferenceCategoryViews.forEach { view ->
            assertEquals(
                view.diffFields.size,
                view.diffFields.map { it.labelRes }.toSet().size,
                "${view.category} must not repeat a label resource",
            )
        }
    }

    // ---------------------------------------------------------------- formatting

    @Test
    fun `booleans render On and Off`() {
        val prefs = snapshot(factorySlices.copy(appearance = factorySlices.appearance.copy(oledMode = true)))
        val factory = snapshot(factorySlices)

        val fields = view(PreferenceResetCategory.APPEARANCE).changedFields(prefs, factory)

        assertEquals("On", fields.first { it.label == Res.string.ss_oled_mode_title.toString() }.currentValue)
        assertEquals("Off", fields.first { it.label == Res.string.ss_oled_mode_title.toString() }.factoryValue)
    }

    @Test
    fun `enum fields with displayName resolve the localized-style name`() {
        val prefs = snapshot(factorySlices.copy(playback = factorySlices.playback.copy(decoderMode = DecoderMode.SW_ONLY)))
        val factory = snapshot(factorySlices)

        val fields = view(PreferenceResetCategory.PLAYBACK).changedFields(prefs, factory)

        val decoder = fields.first { it.label == Res.string.ss_decoder_title.toString() }
        assertEquals("Software Only", decoder.currentValue)
        assertEquals("Hardware (Preferred)", decoder.factoryValue)
    }

    @Test
    fun `durations render in seconds and strengths in percent`() {
        val fields = view(PreferenceResetCategory.PLAYBACK)
            .changedFields(snapshot(factorySlices), snapshot(factorySlices))
        assertEquals(0, fields.size, "sanity: no changes to diff on the factory baseline")

        val seek = view(PreferenceResetCategory.PLAYBACK).diffFields
            .first { it.labelRes == Res.string.ss_seek_duration_title }
        assertEquals("10.0s", seek.value(factorySlices), "default videoSeekDurationMs is 10_000")

        val blueLight = view(PreferenceResetCategory.APPEARANCE).diffFields
            .first { it.labelRes == Res.string.ss_blue_light_strength_title }
        assertEquals("30%", blueLight.value(factorySlices), "default blueLightFilterStrength is 0.3f")
    }

    @Test
    fun `nullable strings fall back to System`() {
        val prefs = snapshot(
            factorySlices.copy(subtitle = factorySlices.subtitle.copy(preferredSubtitleLanguage = "eng")),
            labels = mapOf(Res.string.ss_subtitle_language_title to "Preferred Subtitle Language", Res.string.ss_audio_language_title to "Preferred Audio Language"),
        )
        val factory = snapshot(factorySlices, mapOf(Res.string.ss_subtitle_language_title to "Preferred Subtitle Language", Res.string.ss_audio_language_title to "Preferred Audio Language"))

        val fields = view(PreferenceResetCategory.SUBTITLES_LANGUAGE).fields(prefs, factory)

        assertEquals("eng", fields.first { it.label == "Preferred Subtitle Language" }.currentValue)
        assertEquals("System", fields.first { it.label == "Preferred Subtitle Language" }.factoryValue)
        assertEquals("System", fields.first { it.label == "Preferred Audio Language" }.currentValue)
    }

    @Test
    fun `derived theme-variant flags diff like the former synthwave rows`() {
        val labels = mapOf(
            Res.string.diff_synthwave_mode to "Synthwave Mode",
        )
        val prefs = snapshot(
            factorySlices.copy(appearance = factorySlices.appearance.copy(themeVariant = "synthwave")),
            labels,
        )
        val factory = snapshot(factorySlices, labels)

        assertEquals(
            setOf("Synthwave Mode"),
            changedLabels(prefs, factory, PreferenceResetCategory.APPEARANCE).toSet(),
        )
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
