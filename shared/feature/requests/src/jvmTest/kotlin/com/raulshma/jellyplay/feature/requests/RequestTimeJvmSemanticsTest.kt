package com.raulshma.jellyplay.feature.requests

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 15B pin: the jvmShared actuals of [requestAgeMinutes] /
 * [formatRequestedDate] are the verbatim pre-15B java.time bodies moved out
 * of RequestListItem.kt / RequestDetailBottomSheet.kt — this suite freezes
 * that JVM behavior so a later edit of the actual cannot silently drift from
 * what android + desktop shipped (the wasmJs actuals re-implement the same
 * contract; the browser run stays off, see the build file).
 */
class RequestTimeJvmSemanticsTest {

    // ------------------------------------------------------------------
    // requestAgeMinutes — JVM body: OffsetDateTime.parse + Duration.between
    // ------------------------------------------------------------------

    @Test
    fun `age of a stamp five minutes ago is five-ish`() {
        val stamp = OffsetDateTime.now().minusMinutes(5).toString()
        val age = requestAgeMinutes(stamp)
        assertNotNull(age)
        assertTrue(age in 4..6, "expected ~5 minutes, got $age")
    }

    @Test
    fun `result is offset-independent`() {
        // The comparison is between two absolute instants, so the same moment
        // expressed in different offsets yields the same age.
        val moment = OffsetDateTime.now().withOffsetSameInstant(java.time.ZoneOffset.UTC).minusMinutes(90)
        val utc = requestAgeMinutes(moment.toString())
        val plus2 = requestAgeMinutes(moment.withOffsetSameInstant(java.time.ZoneOffset.ofHours(2)).toString())
        assertEquals(utc, plus2)
    }

    @Test
    fun `future stamps land in the just-now bucket (negative minutes)`() {
        val stamp = OffsetDateTime.now().plusMinutes(10).toString()
        val age = requestAgeMinutes(stamp)
        assertNotNull(age)
        assertTrue(age < 1, "expected < 1 (just-now bucket), got $age")
    }

    @Test
    fun `parse failures return null exactly like the old catch path`() {
        // No zone offset -> OffsetDateTime.parse throws -> null.
        assertNull(requestAgeMinutes("2024-01-05T14:30:00"))
        // Garbage.
        assertNull(requestAgeMinutes("not a timestamp"))
        assertNull(requestAgeMinutes(""))
        // Impossible civil date -> java.time throws -> null.
        assertNull(requestAgeMinutes("2024-02-30T12:00:00Z"))
    }

    // ------------------------------------------------------------------
    // formatRequestedDate — JVM body: LocalDateTime.parse(ISO_DATE_TIME)
    // + ofPattern("MMM d, yyyy"); offset/bracket-zone suffix discarded
    // ------------------------------------------------------------------

    @Test
    fun `formats the local fields`() {
        assertEquals("Jan 5, 2024", formatRequestedDate("2024-01-05T14:30:00"))
        assertEquals("Dec 31, 2023", formatRequestedDate("2023-12-31T23:59:59.123456789"))
    }

    @Test
    fun `offset and bracket zone suffixes are discarded`() {
        assertEquals("Jan 5, 2024", formatRequestedDate("2024-01-05T14:30:00+02:00"))
        assertEquals("Jan 5, 2024", formatRequestedDate("2024-01-05T14:30:00+02:00[Europe/Paris]"))
        assertEquals("Jan 5, 2024", formatRequestedDate("2024-01-05T14:30:00Z"))
    }

    @Test
    fun `format failures return null exactly like the old catch path`() {
        // OffsetDateTime-style stamp without brackets still parses as
        // ISO_DATE_TIME local fields... but garbage / impossible dates don't.
        assertNull(formatRequestedDate("not a timestamp"))
        assertNull(formatRequestedDate(""))
        assertNull(formatRequestedDate("2024-02-30T12:00:00"))
    }

    // ------------------------------------------------------------------
    // formatCount — the String.format replacement (plain %d placeholders)
    // ------------------------------------------------------------------

    @Test
    fun `substitutes positional and plain placeholders`() {
        assertEquals("5m ago", formatCount("%1\$dm ago", 5L))
        assertEquals("5m ago", formatCount("%dm ago", 5L))
        assertEquals("no placeholder", formatCount("no placeholder", 7L))
        // Documented limitation (RequestTime.kt): single-value templates only —
        // other positional slots are left verbatim, as no requests translation
        // uses them for these labels.
        assertEquals("5h %2\$s", formatCount("%1\$dh %2\$s", 5L))
    }

    @Test
    fun `relative-time buckets agree with the old Duration arithmetic`() {
        // Cross-check the RequestListItem bucket boundaries against the exact
        // pre-15B java.time pipeline, computed here from the same stamp.
        val formats = RelativeTimeFormats(
            justNow = "just now",
            minutesAgo = "%1\$dm ago",
            hoursAgo = "%1\$dh ago",
            daysAgo = "%1\$dd ago",
            monthsAgo = "%1\$dmo ago",
            yearsAgo = "%1\$dy ago",
        )
        fun oldBody(dateStr: String): String? = try {
            val date = OffsetDateTime.parse(dateStr)
            val now = OffsetDateTime.now()
            val duration = Duration.between(date, now)
            when {
                duration.toMinutes() < 1 -> formats.justNow
                duration.toMinutes() < 60 -> formats.minutesAgo.format(duration.toMinutes())
                duration.toHours() < 24 -> formats.hoursAgo.format(duration.toHours())
                duration.toDays() < 30 -> formats.daysAgo.format(duration.toDays())
                duration.toDays() < 365 -> formats.monthsAgo.format(duration.toDays() / 30)
                else -> formats.yearsAgo.format(duration.toDays() / 365)
            }
        } catch (_: Exception) {
            null
        }

        // ~2 days old: minutes bucket arithmetic in both pipelines.
        val twoDays = OffsetDateTime.now().minusDays(2).toString()
        assertEquals(oldBody(twoDays), formatRelativeTime(twoDays, formats))
        // Far future: both land in just-now (the JVM truncate-vs-floor delta
        // documented in RequestListItem is unobservable below < 1 minute).
        val future = OffsetDateTime.now().plusHours(3).toString()
        assertEquals(oldBody(future), formatRelativeTime(future, formats))
    }
}
