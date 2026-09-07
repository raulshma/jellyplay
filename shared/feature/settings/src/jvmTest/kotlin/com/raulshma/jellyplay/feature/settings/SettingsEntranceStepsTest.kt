package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the settings root screen's derived entrance steps to the exact literal
 * numbers the hand-typed `settingsSection(key, phoneStep, tvStep)` call sites
 * carried before the derivation replaced them. The screen must not renumber —
 * any drift here is a visible stagger change (each step seeds a 20 ms delay),
 * so every (phone, tv) pair is asserted literally.
 *
 * Shape notes (both pinned):
 *  - `group_screensaver` is TV-only; its phone number (15) is the slot it
 *    occupies in [SETTINGS_ENTRANCE_SECTIONS] but no phone section ever reads
 *    it — the phone axis skips tvOnly entries, which is why `item_experimental`
 *    keeps phone 15 while its tv step shifts to 16.
 *  - `item_notifications` keeps its (10, 10) slot even though the section only
 *    composes where `settingsCapabilities.supportsNotifications` holds — the
 *    literal numbering counts it on every platform (the accepted stagger hole
 *    on desktop), and this pin holds for both capability variants.
 */
class SettingsEntranceStepsTest {

    /** Today's literal (phone, tv) pairs, key-by-key — the pin's authority. */
    private val literalSteps: Map<String, Pair<Int, Int>> = mapOf(
        "profile" to (0 to 0),
        "power_user_mode" to (1 to 1),
        "active_devices" to (2 to 2),
        "account" to (3 to 3),
        "activity" to (4 to 4),
        "system" to (5 to 5),
        "item_appearance" to (6 to 6),
        "item_playback" to (7 to 7),
        "item_audio" to (8 to 8),
        "item_language" to (9 to 9),
        "item_notifications" to (10 to 10),
        "item_storage" to (11 to 11),
        "item_security" to (12 to 12),
        "item_privacy_data" to (13 to 13),
        "item_backup" to (14 to 14),
        "group_screensaver" to (15 to 15),
        "item_experimental" to (15 to 16),
        "item_integrations" to (16 to 17),
        "item_about" to (17 to 18),
    )

    @Test
    fun `every derived step pair equals the hand-typed literal`() {
        literalSteps.forEach { (key, expected) ->
            val (phone, tv) = settingsEntranceStep(key) ?: error("section '$key' missing from SETTINGS_ENTRANCE_SECTIONS")
            assertEquals(expected.first, phone, "$key phone step drifted")
            assertEquals(expected.second, tv, "$key tv step drifted")
        }
    }

    @Test
    fun `the section list declares exactly the pinned sections in order`() {
        assertEquals(literalSteps.keys.toList(), SETTINGS_ENTRANCE_SECTIONS.map { it.key },
            "SETTINGS_ENTRANCE_SECTIONS drifted from the pinned render order")
    }

    @Test
    fun `only the screensaver group is tv-only`() {
        assertEquals(listOf("group_screensaver"), SETTINGS_ENTRANCE_SECTIONS.filter { it.tvOnly }.map { it.key })
    }

    @Test
    fun `the tv-only group shifts only its tv followers`() {
        // Today's hand-typed trio: the tv-only screensaver group costs the
        // sections after it one tv step but no phone step.
        assertEquals(15 to 16, settingsEntranceStep("item_experimental")?.let { it.phone to it.tv })
        assertEquals(16 to 17, settingsEntranceStep("item_integrations")?.let { it.phone to it.tv })
        assertEquals(17 to 18, settingsEntranceStep("item_about")?.let { it.phone to it.tv })
    }

    @Test
    fun `an undeclared key resolves to null so the screen fails fast`() {
        assertNull(settingsEntranceStep("item_that_does_not_exist"))
        assertTrue(SETTINGS_ENTRANCE_SECTIONS.map { it.key }.none { it == "item_that_does_not_exist" })
    }
}
