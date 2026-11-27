package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_account
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_activity_insights
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cat_look_and_feel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cat_media_playback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cat_system_data
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the settings root screen's derived entrance steps and the declared
 * category headers to the ADR-0017 root: a media-first flat list under
 * non-clickable category headers, account pinned top, ONE step axis across
 * phone/TV/desktop (the former separate phone/TV lists are converged onto
 * the single declaration — a hand-maintained second step taxonomy is the
 * wizard-mirror defect). The stagger seeds a 20 ms delay per step, so every
 * index is asserted literally.
 */
class SettingsEntranceStepsTest {

    /** The pinned render order, key-by-key — the pin's authority. */
    private val pinnedOrder: List<String> = listOf(
        "profile",
        "power_user_mode",
        "active_devices",
        "account",
        "activity",
        "item_playback",
        "item_audio",
        "item_language",
        "item_appearance",
        "item_home",
        "item_performance",
        "item_ambient",
        "item_storage",
        "item_downloads",
        "item_notifications",
        "item_security",
        "item_privacy_data",
        "item_backup",
        "system",
        "item_jellyplay_plugin",
        "item_integrations",
        "item_experimental",
        "item_about",
        "item_whatsnew",
    )

    @Test
    fun `every derived step equals the section's declaration index`() {
        pinnedOrder.forEachIndexed { index, key ->
            assertEquals(index, settingsEntranceStep(key), "$key step drifted")
        }
    }

    @Test
    fun `the section list declares exactly the pinned sections in order`() {
        assertEquals(pinnedOrder, SETTINGS_ENTRANCE_SECTIONS.map { it.key },
            "SETTINGS_ENTRANCE_SECTIONS drifted from the pinned render order")
    }

    @Test
    fun `the category headers ride exactly the category lead sections`() {
        val expected = mapOf(
            "active_devices" to Res.string.settings_account,
            "activity" to Res.string.settings_activity_insights,
            "item_playback" to Res.string.settings_cat_media_playback,
            "item_appearance" to Res.string.settings_cat_look_and_feel,
            "item_storage" to Res.string.settings_cat_system_data,
            "item_jellyplay_plugin" to Res.string.settings_integrations,
            "item_about" to Res.string.settings_about,
        )
        val declared = SETTINGS_ENTRANCE_SECTIONS.mapNotNull { section ->
            section.headerRes?.let { section.key to it }
        }.toMap()
        assertEquals(expected, declared, "the category-header carriers drifted")
    }

    @Test
    fun `an undeclared key resolves to null so the screen fails fast`() {
        assertNull(settingsEntranceStep("item_that_does_not_exist"))
        assertTrue(SETTINGS_ENTRANCE_SECTIONS.map { it.key }.none { it == "item_that_does_not_exist" })
    }
}
