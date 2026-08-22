package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.DateFormatPreference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat

class DateFormatHelperTest {

    @Test
    fun `getDateFormat returns SimpleDateFormat for each preference`() {
        DateFormatPreference.entries.forEach { pref ->
            val formatter = getDateFormat(pref)
            assertNotNull(formatter, "Formatter should not be null for $pref")
            assert(formatter is SimpleDateFormat) { "Expected SimpleDateFormat for $pref" }
        }
    }

    @Test
    fun `getDateFormat US produces MM slash dd slash yyyy pattern`() {
        val formatter = getDateFormat(DateFormatPreference.US)
        assertEquals("MM/dd/yyyy", formatter.toPattern())
    }

    @Test
    fun `getDateFormat ISO produces yyyy dash MM dash dd pattern`() {
        val formatter = getDateFormat(DateFormatPreference.ISO)
        assertEquals("yyyy-MM-dd", formatter.toPattern())
    }

    @Test
    fun `getDateFormat EU produces dd slash MM slash yyyy pattern`() {
        val formatter = getDateFormat(DateFormatPreference.EU)
        assertEquals("dd/MM/yyyy", formatter.toPattern())
    }

    @Test
    fun `getDateFormat SHORT produces M dash d dash yy pattern`() {
        val formatter = getDateFormat(DateFormatPreference.SHORT)
        assertEquals("M/d/yy", formatter.toPattern())
    }

    @Test
    fun `formatDate with ISO preference produces expected format`() {
        // January 15, 2024 00:00:00 UTC
        val timestamp = 1705276800000L
        val result = formatDate(timestamp, DateFormatPreference.ISO)
        assertEquals("2024-01-15", result)
    }

    @Test
    fun `formatDate with US preference produces expected format`() {
        val timestamp = 1705276800000L
        val result = formatDate(timestamp, DateFormatPreference.US)
        assertEquals("01/15/2024", result)
    }

    @Test
    fun `formatDate with EU preference produces expected format`() {
        val timestamp = 1705276800000L
        val result = formatDate(timestamp, DateFormatPreference.EU)
        assertEquals("15/01/2024", result)
    }

    @Test
    fun `formatDate with SHORT preference produces expected format`() {
        val timestamp = 1705276800000L
        val result = formatDate(timestamp, DateFormatPreference.SHORT)
        assertEquals("1/15/24", result)
    }

    @Test
    fun `formatDateIso always produces ISO format`() {
        val timestamp = 1705276800000L
        val result = formatDateIso(timestamp)
        assertEquals("2024-01-15", result)
    }

    @Test
    fun `formatDate with zero timestamp`() {
        val result = formatDate(0L, DateFormatPreference.ISO)
        assertEquals("1970-01-01", result)
    }

    @Test
    fun `formatDate defaults to SYSTEM preference`() {
        val timestamp = 1705276800000L
        val result = formatDate(timestamp)
        assertNotNull(result, "Should produce a non-null result with default SYSTEM preference")
        assert(result.isNotEmpty()) { "Result should not be empty" }
    }
}
