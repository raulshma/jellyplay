package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [toEnumOrNull] — the one persisted-string → enum parse behind every
 * preference-store enum read. Exact [Enum.name] matches parse; null (absent
 * key), garbage, blank, and legacy-cased strings return null so the caller's
 * `?:` supplies the documented default — instead of `valueOf` throwing and
 * tripping the store-level catch that wipes every preference to defaults.
 */
class EnumPreferenceParsingTest {

    @Test
    fun `parses valid enum names`() {
        assertEquals(ThemeMode.SYSTEM, "SYSTEM".toEnumOrNull<ThemeMode>())
        assertEquals(ThemeMode.DARK, "DARK".toEnumOrNull<ThemeMode>())
        assertEquals(ThemeMode.SCHEDULED, "SCHEDULED".toEnumOrNull<ThemeMode>())
    }

    @Test
    fun `returns null for a null string (absent key)`() {
        assertNull(null.toEnumOrNull<ThemeMode>())
    }

    @Test
    fun `returns null for garbage and blank values`() {
        assertNull("nonsense".toEnumOrNull<ThemeMode>())
        assertNull("".toEnumOrNull<ThemeMode>())
        assertNull(" ".toEnumOrNull<ThemeMode>())
    }

    @Test
    fun `returns null for legacy-cased values (name match is case-sensitive)`() {
        assertNull("dark".toEnumOrNull<ThemeMode>())
        assertNull("Dark".toEnumOrNull<ThemeMode>())
    }
}
