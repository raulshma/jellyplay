package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [EpisodeStreamInfo.hasDisplayableBadges], which gates whether
 * stream-info badges (CC, audio layout, audio language count) render along
 * the right side of the episode time line.
 */
class EpisodeStreamBadgesTest {

    @Test
    fun `hasSubtitles true yields displayable badges`() {
        val info = EpisodeStreamInfo(
            hasSubtitles = true,
            audioLayout = null,
            audioLanguageCount = 1,
        )
        assertTrue(info.hasDisplayableBadges)
    }

    @Test
    fun `audioLayout non-null yields displayable badges`() {
        val info = EpisodeStreamInfo(
            hasSubtitles = false,
            audioLayout = "5.1",
            audioLanguageCount = 1,
        )
        assertTrue(info.hasDisplayableBadges)
    }

    @Test
    fun `audioLanguageCount greater than one yields displayable badges`() {
        val info = EpisodeStreamInfo(
            hasSubtitles = false,
            audioLayout = null,
            audioLanguageCount = 2,
        )
        assertTrue(info.hasDisplayableBadges)
    }

    @Test
    fun `no subtitles single audio language and null layout yields no displayable badges`() {
        val info = EpisodeStreamInfo(
            hasSubtitles = false,
            audioLayout = null,
            audioLanguageCount = 1,
        )
        assertFalse(info.hasDisplayableBadges)
    }
}
