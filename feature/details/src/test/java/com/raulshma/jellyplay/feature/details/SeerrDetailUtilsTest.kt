package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.seerr.SeerrAggregateCast
import com.raulshma.jellyplay.core.model.seerr.SeerrCast
import com.raulshma.jellyplay.core.model.seerr.SeerrRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
