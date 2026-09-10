package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_token
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the landing-page summary builders (SettingsScreen.kt) — plain policy
 * functions emitting renderable parts, so no composer is needed here. The
 * fake part renderer maps resource identity to its stable `key` label, which
 * keeps the pins honest about WHICH resource every token resolves through
 * while leaving the actual policy under test — which prefs appear, their
 * order, the ", " separator, the all-defaults shape and the contrast-suffix
 * casing — fully observable.
 */
class SettingsSummariesTest {

    /** Fake renderer: literals pass through, resources render as labels. */
    private fun renderPart(part: SettingsSummaryPart): String = when (part) {
        is SettingsSummaryPart.Literal -> part.text
        is SettingsSummaryPart.Token -> part.resource.key
        is SettingsSummaryPart.Formatted -> "${part.resource.key}(${part.arg})"
        is SettingsSummaryPart.Plural -> "${part.resource.key}:${part.count}"
    }

    private fun renderAppearance(preferences: SettingsScreenPreferences): String =
        joinSummaryTokens(appearanceSummaryParts(preferences).map(::renderPart))

    private fun renderExperimental(preferences: SettingsScreenPreferences): String =
        joinSummaryTokens(experimentalSummaryParts(preferences).map(::renderPart))

    // --------------------------------------------------------------- appearance

    @Test
    fun `appearance defaults - theme first, dynamic token, nothing else`() {
        assertEquals(
            "System, settings_dynamic_token",
            renderAppearance(SettingsScreenPreferences()),
        )
    }

    @Test
    fun `appearance order - theme, dynamic, oled, contrast, performance`() {
        val everythingOn = SettingsScreenPreferences(
            themeMode = ThemeMode.DARK,
            dynamicTheming = true,
            oledMode = true,
            contrastLevel = ContrastLevel.HIGH,
            performanceMode = true,
        )
        assertEquals(
            "Dark, settings_dynamic_token, settings_oled_token, " +
                "settings_contrast_suffix(High), settings_performance_token",
            renderAppearance(everythingOn),
        )
    }

    @Test
    fun `appearance parts are exactly the enabled tokens in order`() {
        val everythingOn = SettingsScreenPreferences(
            themeMode = ThemeMode.DARK,
            dynamicTheming = true,
            oledMode = true,
            contrastLevel = ContrastLevel.HIGH,
            performanceMode = true,
        )
        assertEquals(
            listOf(
                SettingsSummaryPart.Literal("Dark"),
                SettingsSummaryPart.Token(Res.string.settings_dynamic_token),
                SettingsSummaryPart.Token(Res.string.settings_oled_token),
                SettingsSummaryPart.Formatted(Res.string.settings_contrast_suffix, "High"),
                SettingsSummaryPart.Token(Res.string.settings_performance_token),
            ),
            appearanceSummaryParts(everythingOn),
        )
    }

    @Test
    fun `appearance disabled prefs drop out - lone token carries no separator`() {
        val minimal = SettingsScreenPreferences(
            themeMode = ThemeMode.LIGHT,
            dynamicTheming = false,
            contrastLevel = ContrastLevel.DEFAULT,
        )
        assertEquals("Light", renderAppearance(minimal))
    }

    @Test
    fun `appearance theme mode is title-cased from the enum name`() {
        val scheduled = SettingsScreenPreferences(themeMode = ThemeMode.SCHEDULED, dynamicTheming = false)
        assertEquals("Scheduled", renderAppearance(scheduled))
    }

    @Test
    fun `contrast suffix title-cases the enum name`() {
        val medium = SettingsScreenPreferences(contrastLevel = ContrastLevel.MEDIUM)
        assertEquals(
            "System, settings_dynamic_token, settings_contrast_suffix(Medium)",
            renderAppearance(medium),
        )
    }

    // ------------------------------------------------------------- experimental

    @Test
    fun `experimental defaults - early-access placeholder, no plural`() {
        assertEquals(
            "settings_early_access_features",
            renderExperimental(SettingsScreenPreferences()),
        )
    }

    @Test
    fun `experimental plural carries the enabled feature count`() {
        val threeEnabled = SettingsScreenPreferences(
            enabledExperimentalFeatures = setOf(
                ExperimentalFeature.HOME_CARD_CLIPPING,
                ExperimentalFeature.MEDIA_CARD_PEEK,
                ExperimentalFeature.DIRECT_ARR_INTEGRATION,
            ),
        )
        assertEquals(
            "settings_features_enabled:3",
            renderExperimental(threeEnabled),
        )
    }

    @Test
    fun `experimental single feature still uses the plural`() {
        val oneEnabled = SettingsScreenPreferences(
            enabledExperimentalFeatures = setOf(ExperimentalFeature.MEDIA_CARD_PEEK),
        )
        assertEquals(
            "settings_features_enabled:1",
            renderExperimental(oneEnabled),
        )
    }
}
