package com.raulshma.jellyplay.feature.details

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Circle
import com.composables.icons.tabler.outline.CloudDownload
import com.composables.icons.tabler.outline.Ticket
import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCast
import com.raulshma.jellyplay.core.model.seerr.SeerrCast
import com.raulshma.jellyplay.core.model.seerr.SeerrRole
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_release_digital
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_release_physical
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_release_theatrical
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Tests the Seerr detail UI helpers — the neutral cast-member mappers, the
 * YouTube thumbnail builder, and the runtime/date formatters that delegate to
 * the shared core/ui formatters. Previously these were untested pure helpers.
 */
class SeerrDetailUtilsTest {

    // ── Cast-member mapping (removes the List<Any> smell) ──────────────

    @Test
    fun `aggregate cast maps role character to neutral member`() {
        val cast = listOf(
            SeerrAggregateCast(
                id = 1,
                name = "Actor One",
                profilePath = "/abc.jpg",
                roles = listOf(SeerrRole(character = "Hero")),
            ),
        )

        val members = cast.toAggregateCastMembers()

        assertEquals(1, members.size)
        assertEquals(SeerrCastMember(1, "Actor One", "Hero", "https://image.tmdb.org/t/p/h632/abc.jpg"), members[0])
    }

    @Test
    fun `aggregate cast with no roles yields blank character`() {
        val members = listOf(SeerrAggregateCast(id = 2, name = "Actor Two")).toAggregateCastMembers()

        assertEquals("", members[0].character)
        assertNull(members[0].profileUrl)
    }

    @Test
    fun `movie cast maps character to neutral member`() {
        val cast = listOf(
            SeerrCast(id = 3, name = "Actor Three", character = "Villain", profilePath = "/def.jpg"),
        )

        val members = cast.toCastMembers()

        assertEquals(1, members.size)
        assertEquals("Villain", members[0].character)
        assertEquals("Actor Three", members[0].name)
    }

    @Test
    fun `movie cast with null character yields blank`() {
        val members = listOf(SeerrCast(id = 4, name = "Actor Four", character = null)).toCastMembers()

        assertEquals("", members[0].character)
    }

    @Test
    fun `cast mapping preserves order and dedups by id`() {
        val cast = listOf(
            SeerrCast(id = 1, name = "A"),
            SeerrCast(id = 2, name = "B"),
            SeerrCast(id = 1, name = "A duplicate"),
        )

        val members = cast.toCastMembers()

        assertEquals(listOf(1, 2, 1), members.map { it.id })
    }

    // ── YouTube thumbnail builder ──────────────────────────────────────

    @Test
    fun `youtube thumbnail builds url for youtube site`() {
        assertEquals(
            "https://img.youtube.com/vi/abc123/mqdefault.jpg",
            youTubeThumbnailUrl("youtube", "abc123"),
        )
    }

    @Test
    fun `youtube thumbnail is case-insensitive on site`() {
        assertEquals(
            "https://img.youtube.com/vi/abc/mqdefault.jpg",
            youTubeThumbnailUrl("YouTube", "abc"),
        )
    }

    @Test
    fun `youtube thumbnail null for non-youtube site`() {
        assertNull(youTubeThumbnailUrl("vimeo", "abc"))
    }

    @Test
    fun `youtube thumbnail null for blank key`() {
        assertNull(youTubeThumbnailUrl("youtube", ""))
        assertNull(youTubeThumbnailUrl("youtube", null))
    }

    @Test
    fun `youtube thumbnail null for null site`() {
        assertNull(youTubeThumbnailUrl(null, "abc"))
    }

    // ── Runtime formatting (delegates to core/ui) ─────────────────────

    @Test
    fun `formatRuntime renders hours and minutes`() {
        assertEquals("1h 30m", formatRuntime(90))
    }

    @Test
    fun `formatRuntime renders whole hours without minutes`() {
        assertEquals("2h", formatRuntime(120))
    }

    @Test
    fun `formatRuntime renders minutes under an hour`() {
        assertEquals("45m", formatRuntime(45))
    }

    @Test
    fun `formatRuntime zero is zero minutes`() {
        assertEquals("0m", formatRuntime(0))
    }

    // ── Date formatting ────────────────────────────────────────────────

    @Test
    fun `formatDate renders ISO date as short locale form`() {
        assertEquals("Jul 29, 2026", formatDate("2026-07-29"))
    }

    @Test
    fun `formatDate handles single digit months and days`() {
        assertEquals("Jan 5, 2025", formatDate("2025-01-05"))
    }

    @Test
    fun `formatDate falls back to first 10 chars on garbage input`() {
        // Not a parseable date — return the first 10 chars verbatim.
        assertEquals("not_a_date", formatDate("not_a_date_here"))
    }

    @Test
    fun `formatDate falls back to first 10 chars when not a date`() {
        // LocalDate parsing is strict: non-date-shaped input throws and the
        // fallback returns the first 10 chars verbatim.
        assertEquals("totally!!!", formatDate("totally!!!"))
    }

    @Test
    fun `formatDate falls back to first 10 chars for empty input`() {
        // Empty string is not a parseable date — returns the first 10 chars (the
        // empty string) without throwing.
        assertEquals("", formatDate(""))
    }

    // ── Flag emoji ─────────────────────────────────────────────────────

    @Test
    fun `getFlagEmoji converts two-letter country code to regional indicators`() {
        // "US" → 🇺🇸 (regional indicator U+S = U+1F1FA U+1F1F8).
        assertEquals("🇺🇸", getFlagEmoji("US"))
    }

    @Test
    fun `getFlagEmoji null for non-two-letter code`() {
        assertNull(getFlagEmoji("USA"))
        assertNull(getFlagEmoji("X"))
    }

    @Test
    fun `getFlagEmoji null for empty string`() {
        // Empty/blank input never has two chars — guard returns null.
        assertNull(getFlagEmoji(""))
    }

    @Test
    fun `getFlagEmoji is uppercase-only lowercase inputs do not map to flags`() {
        // The calc subtracts 'A' (0x41), so lowercase 'g' (0x67) resolves outside
        // the regional-indicator range (0x1F1E6–0x1F1FF) and produces a different
        // codepoint — the caller must pass uppercase ISO codes.
        assertFalse(getFlagEmoji("gb") == "🇬🇧")
    }

    // ── Rating formatting (moved from SeerrDetailScreen) ──────────────

    @Test
    fun `formatRatingOneDecimal renders one decimal with trailing zero`() {
        assertEquals("8.0", formatRatingOneDecimal(8.0f))
        assertEquals("0.0", formatRatingOneDecimal(0f))
        assertEquals("10.0", formatRatingOneDecimal(10.0f))
    }

    @Test
    fun `formatRatingOneDecimal truncates toward the rendered tenth`() {
        // 8.74f is 8.7399…96 in binary: ·10+0.5 → 87.89… → 87 → "8.7".
        assertEquals("8.7", formatRatingOneDecimal(8.74f))
        // 8.76f is 8.7600…09 in binary: ·10+0.5 → 88.10… → 88 → "8.8".
        assertEquals("8.8", formatRatingOneDecimal(8.76f))
    }

    @Test
    fun `formatRatingOneDecimal rounds exact binary ties away from zero`() {
        // 0.25f/8.25f are exact binary fractions, so ·10 lands on the exact
        // tie 2.5/82.5; HALF_UP pushes it to 3/83 (java's "%.1f" rule on the
        // same value — and the magnitude arithmetic is symmetric for 8.75f).
        assertEquals("0.3", formatRatingOneDecimal(0.25f))
        assertEquals("0.8", formatRatingOneDecimal(0.75f))
        assertEquals("8.3", formatRatingOneDecimal(8.25f))
        assertEquals("8.8", formatRatingOneDecimal(8.75f))
    }

    @Test
    fun `formatRatingOneDecimal applies the sign symmetrically for negatives`() {
        assertEquals("-8.7", formatRatingOneDecimal(-8.74f))
    }

    @Test
    fun `formatRatingOneDecimal always renders the dot never a locale comma`() {
        // The former episode-row site used default-locale String.format, so
        // de/fr JVM devices saw "8,7" — the helper pins the dot.
        assertEquals("8.7", formatRatingOneDecimal(8.74f))
    }

    // ── US currency formatting (moved from SeerrDetailScreen) ─────────

    @Test
    fun `formatUsCurrency renders plain dollar amounts with two decimals`() {
        assertEquals("$8.00", formatUsCurrency(8))
        assertEquals("$0.00", formatUsCurrency(0))
        assertEquals("$1.00", formatUsCurrency(1))
    }

    @Test
    fun `formatUsCurrency comma-groups thousands`() {
        assertEquals("$1,000.00", formatUsCurrency(1_000))
        assertEquals("$999.00", formatUsCurrency(999))
        assertEquals("$250,000,000.00", formatUsCurrency(250_000_000))
    }

    @Test
    fun `formatUsCurrency puts the minus before the dollar sign`() {
        // NumberFormat.getCurrencyInstance(Locale.US) renders "-$5,000.00".
        assertEquals("-$5,000.00", formatUsCurrency(-5_000))
    }

    // ── Release-type marker table (moved from SeerrDetailScreen) ──────

    @Test
    fun `releaseTypePresentation maps theatrical digital and physical`() {
        assertEquals(
            ReleaseTypePresentation(Tabler.Outline.Ticket, Res.string.detail_cd_release_theatrical),
            releaseTypePresentation(3),
        )
        assertEquals(
            ReleaseTypePresentation(Tabler.Outline.CloudDownload, Res.string.detail_cd_release_digital),
            releaseTypePresentation(4),
        )
        assertEquals(
            ReleaseTypePresentation(Tabler.Outline.Circle, Res.string.detail_cd_release_physical),
            releaseTypePresentation(5),
        )
    }

    @Test
    fun `releaseTypePresentation renders nothing outside TMDB types 3 to 5`() {
        // The row filters to 3..5 upstream; the table keeps the old `when`'s
        // fall-through (no marker) for everything else.
        assertNull(releaseTypePresentation(1))
        assertNull(releaseTypePresentation(2))
        assertNull(releaseTypePresentation(6))
        assertNull(releaseTypePresentation(0))
    }
}
