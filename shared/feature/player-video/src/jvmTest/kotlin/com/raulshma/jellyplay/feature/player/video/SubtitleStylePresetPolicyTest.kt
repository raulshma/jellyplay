package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure preset policy ([SubtitleStylePresetPolicy]): the built-in
 * quartet's shapes, the user-list bookkeeping (blank-name no-op, same-name
 * replace-in-place, append + oldest eviction under the cap, case-insensitive
 * delete), and the application fold (look forced authoritative, per-item sync
 * offset carried over — a preset must never move the delay).
 */
class SubtitleStylePresetPolicyTest {

    private fun style(fontSize: Int = 24, offsetMs: Long = 0L, applyCustomStyle: Boolean = false) =
        SubtitleStyle(fontSize = fontSize, offsetMs = offsetMs, applyCustomStyle = applyCustomStyle)

    // ── Built-ins ──────────────────────────────────────────────────────────

    @Test
    fun builtIns_exactlyFour_withStableIds() {
        assertEquals(
            listOf("subtle", "big_bold", "classic_yellow", "netflixish"),
            SubtitleStylePresetPolicy.builtIns.map { it.id },
        )
    }

    @Test
    fun builtIns_forceCustomStyleOn() {
        // A preset pick must activate custom styling even when the user saved
        // the global override off — otherwise the apply would be a no-op.
        for (preset in SubtitleStylePresetPolicy.builtIns) {
            assertTrue(preset.style.applyCustomStyle, preset.id)
        }
    }

    @Test
    fun builtIns_areVisuallyDistinct() {
        val styles = SubtitleStylePresetPolicy.builtIns.map { it.style }
        // Size / colour / edge / background each differentiate at least one pair.
        assertTrue(styles.map { it.fontSize }.toSet().size >= 3)
        assertTrue(styles.any { it.fontColor == SubtitleColor.YELLOW })
        assertTrue(styles.any { it.edgeType == SubtitleEdgeType.OUTLINE })
        assertTrue(styles.any { it.backgroundOpacity > 0f })
        assertTrue(styles.none { it.offsetMs != 0L })
    }

    // ── saveUser ───────────────────────────────────────────────────────────

    @Test
    fun saveUser_blankName_noOp() {
        val presets = listOf(SubtitleStylePreset("Mine", style()))
        assertEquals(presets, SubtitleStylePresetPolicy.saveUser(presets, "   ", style()))
        assertEquals(emptyList(), SubtitleStylePresetPolicy.saveUser(emptyList(), "", style()))
    }

    @Test
    fun saveUser_newName_appendsTrimmed() {
        val updated = SubtitleStylePresetPolicy.saveUser(emptyList(), "  Mine  ", style(fontSize = 30))
        assertEquals(listOf(SubtitleStylePreset("Mine", style(fontSize = 30))), updated)
    }

    @Test
    fun saveUser_sameName_replacesInPlace() {
        val presets = listOf(
            SubtitleStylePreset("A", style(fontSize = 20)),
            SubtitleStylePreset("B", style(fontSize = 22)),
        )
        val updated = SubtitleStylePresetPolicy.saveUser(presets, "a", style(fontSize = 44))
        // Case-insensitive match, position preserved, neighbours untouched;
        // the stored name becomes the user's typed casing (a rename).
        assertEquals(listOf(SubtitleStylePreset("a", style(fontSize = 44)), presets[1]), updated)
    }

    @Test
    fun saveUser_beyondCap_evictsTheOldest() {
        var presets = emptyList<SubtitleStylePreset>()
        for (i in 1..SubtitleStylePresetPolicy.MAX_USER_PRESETS) {
            presets = SubtitleStylePresetPolicy.saveUser(presets, "P$i", style())
        }
        assertEquals(SubtitleStylePresetPolicy.MAX_USER_PRESETS, presets.size)
        assertEquals("P1", presets.first().name)

        presets = SubtitleStylePresetPolicy.saveUser(presets, "P9", style())
        assertEquals(SubtitleStylePresetPolicy.MAX_USER_PRESETS, presets.size)
        assertEquals("P2", presets.first().name) // oldest evicted
        assertEquals("P9", presets.last().name)
    }

    @Test
    fun saveUser_capReplaceDoesNotEvict() {
        var presets = emptyList<SubtitleStylePreset>()
        for (i in 1..SubtitleStylePresetPolicy.MAX_USER_PRESETS) {
            presets = SubtitleStylePresetPolicy.saveUser(presets, "P$i", style())
        }
        // Re-saving the oldest name updates in place; nothing evicts.
        presets = SubtitleStylePresetPolicy.saveUser(presets, "P1", style(fontSize = 48))
        assertEquals(SubtitleStylePresetPolicy.MAX_USER_PRESETS, presets.size)
        assertEquals("P1", presets.first().name)
        assertEquals("P2", presets[1].name)
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    fun delete_removesByName_caseInsensitive_andTrims() {
        val presets = listOf(
            SubtitleStylePreset("Mine", style()),
            SubtitleStylePreset("Yours", style()),
        )
        assertEquals(
            listOf(SubtitleStylePreset("Yours", style())),
            SubtitleStylePresetPolicy.delete(presets, " mine "),
        )
    }

    @Test
    fun delete_unknownName_noOp() {
        val presets = listOf(SubtitleStylePreset("Mine", style()))
        assertEquals(presets, SubtitleStylePresetPolicy.delete(presets, "Nope"))
    }

    // ── appliedStyle ───────────────────────────────────────────────────────

    @Test
    fun appliedStyle_forcesCustomStyleOn_andCarriesTheCurrentOffset() {
        val preset = SubtitleStylePreset(
            "Mine",
            SubtitleStyle(fontSize = 40, offsetMs = 9999L, applyCustomStyle = false),
        )
        val applied = SubtitleStylePresetPolicy.appliedStyle(
            preset,
            currentStyle = style(offsetMs = -1500L),
        )
        assertEquals(40, applied.fontSize)
        assertTrue(applied.applyCustomStyle)
        // The per-item sync delay never travels with a look.
        assertEquals(-1500L, applied.offsetMs)
        assertFalse(applied.offsetMs == preset.style.offsetMs)
    }
}
