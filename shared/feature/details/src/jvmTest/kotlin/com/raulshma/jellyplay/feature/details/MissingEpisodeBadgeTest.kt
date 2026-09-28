package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MissingEpisodeReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests the virtual-episode badge derivation ([missingEpisodeBadge]) that the
 * season-view episode rows render: unaired episodes badge their air date,
 * everything else badges "Missing". Pure decision — no Compose.
 */
class MissingEpisodeBadgeTest {

    @Test
    fun `unaired reason with parseable premiere date yields Airs badge`() {
        val badge = missingEpisodeBadge(MissingEpisodeReason.UNAIRED, "2026-10-05T00:00:00")

        val airs = assertIs<MissingEpisodeBadge.Airs>(badge)
        assertEquals(2026, airs.date.year)
        assertEquals(10, airs.date.monthNumber)
        assertEquals(5, airs.date.dayOfMonth)
    }

    @Test
    fun `missing-file reason yields Missing badge even with a premiere date`() {
        val badge = missingEpisodeBadge(MissingEpisodeReason.MISSING_FILE, "2026-10-05T00:00:00")

        assertEquals(MissingEpisodeBadge.Missing, badge)
    }

    @Test
    fun `unaired reason with null or unparseable premiere date degrades to Missing`() {
        assertEquals(MissingEpisodeBadge.Missing, missingEpisodeBadge(MissingEpisodeReason.UNAIRED, null))
        assertEquals(MissingEpisodeBadge.Missing, missingEpisodeBadge(MissingEpisodeReason.UNAIRED, "not-a-date"))
    }

    @Test
    fun `null reason (non-virtual item) yields Missing badge`() {
        assertEquals(MissingEpisodeBadge.Missing, missingEpisodeBadge(null, null))
    }
}
