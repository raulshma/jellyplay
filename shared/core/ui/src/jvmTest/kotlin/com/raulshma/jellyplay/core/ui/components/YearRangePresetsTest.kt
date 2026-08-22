package com.raulshma.jellyplay.core.ui.components

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class YearRangePresetsTest {

    private val fixedPresets: List<YearRangePreset> = listOf(
        YearRangePreset("1920-1949", "1920s–40s", 1920..1949),
        YearRangePreset("1950-1959", "1950s", 1950..1959),
        YearRangePreset("1960-1969", "1960s", 1960..1969),
        YearRangePreset("1970-1979", "1970s", 1970..1979),
        YearRangePreset("1980-1989", "1980s", 1980..1989),
        YearRangePreset("1990-1999", "1990s", 1990..1999),
        YearRangePreset("2000-2009", "2000s", 2000..2009),
        YearRangePreset("2010-2019", "2010s", 2010..2019),
        YearRangePreset("2020-2026", "2020s–now", 2020..2026),
    )

    @Test
    fun `yearRangePresets builds catalog anchored at current year`() {
        val presets = yearRangePresets(now = 2026)
        assertEquals("1920-1949", presets.first().id)
        assertEquals(2020..2026, presets.last().years)
    }

    @Test
    fun `parseYearRangePreset expands valid range`() {
        assertEquals((1990..1999).toList(), parseYearRangePreset("1990-1999"))
    }

    @Test
    fun `parseYearRangePreset returns empty for invalid input`() {
        assertEquals(emptyList<Int>(), parseYearRangePreset(""))
        assertEquals(emptyList<Int>(), parseYearRangePreset("not-a-range"))
        assertEquals(emptyList<Int>(), parseYearRangePreset("1990"))
        assertEquals(emptyList<Int>(), parseYearRangePreset("2000-1990"))
    }

    @Test
    fun `findYearRangePresetId matches exact decade set`() {
        assertEquals("1990-1999", findYearRangePresetId((1990..1999).toList(), fixedPresets))
    }

    @Test
    fun `findYearRangePresetId returns ANY_ID when subset of decade`() {
        // A single year like 2022 doesn't match the full 2020s decade — caller must
        // preserve the value separately so custom selections survive round-trips.
        assertEquals(YearRangePreset.ANY_ID, findYearRangePresetId(listOf(2022), fixedPresets))
    }

    @Test
    fun `findYearRangePresetId returns ANY_ID when empty`() {
        assertEquals(YearRangePreset.ANY_ID, findYearRangePresetId(emptyList(), fixedPresets))
    }

    @Test
    fun `yearPresetSelection reports Full when all years present`() {
        assertEquals(
            YearPresetSelection.Full,
            yearPresetSelection(fixedPresets.first { it.id == "1990-1999" }, (1990..1999).toList()),
        )
    }

    @Test
    fun `yearPresetSelection reports Partial when only some years present`() {
        assertEquals(
            YearPresetSelection.Partial,
            yearPresetSelection(fixedPresets.first { it.id == "1990-1999" }, listOf(1995, 1996)),
        )
    }

    @Test
    fun `yearPresetSelection reports Unselected when none present`() {
        assertEquals(
            YearPresetSelection.Unselected,
            yearPresetSelection(fixedPresets.first { it.id == "1990-1999" }, listOf(1985)),
        )
    }

    @Test
    fun `toggleYearPreset adds decade years when partially selected`() {
        val result = toggleYearPreset(
            preset = fixedPresets.first { it.id == "1990-1999" },
            years = listOf(1995, 2001),
        )
        assertTrue((1990..1999).all { it in result })
        assertTrue(2001 in result)
    }

    @Test
    fun `toggleYearPreset removes decade years when fully selected`() {
        val result = toggleYearPreset(
            preset = fixedPresets.first { it.id == "1990-1999" },
            years = (1990..1999).toList() + 2001,
        )
        assertTrue((1990..1999).none { it in result })
        assertTrue(2001 in result)
    }

    @Test
    fun `toggleYearPreset preserves years from other decades`() {
        val initial = setOf(1985, 1987, 2001, 2005)
        val result = toggleYearPreset(
            preset = fixedPresets.first { it.id == "1990-1999" },
            years = initial,
        )
        // 80s and 2000s selections survive untouched.
        assertTrue(1985 in result && 1987 in result)
        assertTrue(2001 in result && 2005 in result)
    }
}
