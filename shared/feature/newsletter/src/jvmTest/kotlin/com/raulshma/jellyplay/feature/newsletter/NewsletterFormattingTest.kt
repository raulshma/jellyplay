package com.raulshma.jellyplay.feature.newsletter

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import java.lang.reflect.Method

/**
 * Coverage for the newsletter formatting helpers pinned by the conveyor
 * brief: formatCount's K/M suffixes (NewsletterAggregatedStats, hardcoded
 * English preserved from HEAD) and formatRelativeDate's Today/Yesterday/
 * formatted-date/parse-failure branches (NewsletterActivityDigest). Both are
 * file-private top-level functions — reached through reflection so the moved
 * screens stay byte-identical instead of being widened to internal for the
 * tests.
 */
class NewsletterFormattingTest {

    private fun formatCount(count: Long): String =
        privateTopLevel("NewsletterAggregatedStatsKt", "formatCount", Long::class.java)
            .invoke(null, count) as String

    private fun formatRelativeDate(dateStr: String): String =
        privateTopLevel("NewsletterActivityDigestKt", "formatRelativeDate", String::class.java)
            .invoke(null, dateStr) as String

    private fun privateTopLevel(fileClass: String, name: String, vararg params: Class<*>): Method =
        Class.forName("com.raulshma.jellyplay.feature.newsletter.$fileClass")
            .getDeclaredMethod(name, *params)
            .apply { isAccessible = true }

    @Test
    fun `formatCount plain numbers pass through`() {
        assertEquals("0", formatCount(0))
        assertEquals("42", formatCount(42))
        assertEquals("999", formatCount(999))
    }

    @Test
    fun `formatCount uses one decimal K suffix from 1_000`() {
        assertEquals("1.0K", formatCount(1_000))
        assertEquals("1.2K", formatCount(1_234))
        assertEquals("999.9K", formatCount(999_999))
    }

    @Test
    fun `formatCount uses one decimal M suffix from 1_000_000`() {
        assertEquals("1.0M", formatCount(1_000_000))
        assertEquals("1.2M", formatCount(1_250_000))
        assertEquals("12.3M", formatCount(12_300_000))
    }

    @Test
    fun `formatRelativeDate labels today and yesterday in system zone`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now().atStartOfDay(zone).toInstant()
        assertEquals("Today", formatRelativeDate(today.toString()))

        val yesterday = LocalDate.now().minusDays(1).atStartOfDay(zone).toInstant()
        assertEquals("Yesterday", formatRelativeDate(yesterday.toString()))
    }

    @Test
    fun `formatRelativeDate formats older dates as MMM d`() {
        val zone = ZoneId.systemDefault()
        val older = LocalDate.now().minusDays(3).atStartOfDay(zone).toInstant()

        val expected = LocalDate.now().minusDays(3)
            .format(DateTimeFormatter.ofPattern("MMM d"))
        assertEquals(expected, formatRelativeDate(older.toString()))
    }

    @Test
    fun `formatRelativeDate returns the raw string when parsing fails`() {
        assertEquals("not-a-date", formatRelativeDate("not-a-date"))
        assertEquals("", formatRelativeDate(""))
    }

    @Test
    fun `formatRelativeDate handles an Instant with time components`() {
        val zone = ZoneId.systemDefault()
        // Same calendar day, non-midnight time — still "Today".
        val todayNoon = LocalDate.now().atStartOfDay(zone).plusSeconds(13_977).toInstant()
        assertEquals("Today", formatRelativeDate(todayNoon.toString()))
        // Sanity: the string we built really is an ISO instant.
        Instant.parse(todayNoon.toString())
    }
}
