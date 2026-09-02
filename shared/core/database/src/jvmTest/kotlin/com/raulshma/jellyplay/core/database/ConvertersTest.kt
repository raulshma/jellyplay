package com.raulshma.jellyplay.core.database

import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure-unit coverage for the Room [Converters] object: list/enum column
 * encoding plus the legacy comma-separated decode path that has to keep
 * reading pre-JSON rows written by older schema versions.
 */
class ConvertersTest {

    private enum class SampleEnum { ALPHA, BETA }

    @Test
    fun `string list round-trips through JSON`() {
        val value = listOf("Action", "Comedy", "Sci-Fi")
        val encoded = Converters.fromStringList(value)
        assertEquals(value, Converters.toStringList(encoded))
    }

    @Test
    fun `string list round-trip survives JSON-special characters`() {
        val value = listOf("a,b", "quote\"inside", "bracket[1]", "unicode ★")
        val encoded = Converters.fromStringList(value)
        assertEquals(value, Converters.toStringList(encoded))
    }

    @Test
    fun `fromStringList of null encodes to null`() {
        assertNull(Converters.fromStringList(null))
    }

    @Test
    fun `toStringList decodes legacy comma-separated values`() {
        assertEquals(listOf("a", "b", "c"), Converters.toStringList("a,b,c"))
    }

    @Test
    fun `toStringList drops empty segments in legacy values`() {
        assertEquals(listOf("a", "b"), Converters.toStringList("a,,b"))
    }

    @Test
    fun `toStringList decodes JSON arrays`() {
        assertEquals(listOf("x", "y"), Converters.toStringList("""["x","y"]"""))
    }

    @Test
    fun `toStringList returns null for null or empty input`() {
        assertNull(Converters.toStringList(null))
        assertNull(Converters.toStringList(""))
    }

    @Test
    fun `toStringList returns null for malformed JSON array`() {
        assertNull(Converters.toStringList("[broken"))
    }

    @Test
    fun `toStringList treats non-JSON garbage as a single legacy value`() {
        assertEquals(listOf("not json at all"), Converters.toStringList("not json at all"))
    }

    @Test
    fun `int list round-trips through comma encoding`() {
        val value = listOf(1, 2, 3)
        assertEquals("1,2,3", Converters.fromIntList(value))
        assertEquals(value, Converters.toIntList(Converters.fromIntList(value)))
    }

    @Test
    fun `fromIntList of null encodes to null`() {
        assertNull(Converters.fromIntList(null))
    }

    @Test
    fun `toIntList decodes JSON arrays with whitespace`() {
        assertEquals(listOf(1, 2), Converters.toIntList("[1, 2]"))
    }

    @Test
    fun `toIntList drops non-numeric legacy segments`() {
        assertEquals(listOf(1, 3), Converters.toIntList("1,x,3"))
    }

    @Test
    fun `toIntList returns null for null or empty input`() {
        assertNull(Converters.toIntList(null))
        assertNull(Converters.toIntList(""))
    }

    @Test
    fun `toIntList returns null for malformed JSON array`() {
        assertNull(Converters.toIntList("[1,"))
    }

    @Test
    fun `fromEnum stores the enum name`() {
        assertEquals("ALPHA", Converters.fromEnum(SampleEnum.ALPHA))
    }

    @Test
    fun `fromEnum of null is null`() {
        assertNull(Converters.fromEnum(null))
    }

    @Test
    fun `home sections result round-trips`() {
        val value = HomeSectionsResult(
            sections = emptyList(),
            failedSectionTypes = setOf(HomeSectionType.FAVORITES, HomeSectionType.NEXT_UP),
        )
        val encoded = Converters.encodeHomeSectionsResult(value)
        assertEquals(value, Converters.decodeHomeSectionsResult(encoded))
    }

    @Test
    fun `decodeHomeSectionsResult returns null for garbage`() {
        assertNull(Converters.decodeHomeSectionsResult("<html>not json</html>"))
    }
}
