package com.raulshma.jellyplay.core.model.subtitle

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins the saved-subtitle ↔ server-stream attribute matching that backs the
 * delete-then-purge flow: a subtitle deleted from the metadata editor must
 * purge exactly its local durable copy (and playback must skip entries whose
 * server stream vanished), so both over-matching (purging unrelated copies)
 * and under-matching (ghosts resurrecting) are regressions.
 */
class SavedSubtitleStreamMatchingTest {

    private fun saved(
        language: String? = "eng",
        codec: String? = "srt",
        isForced: Boolean = false,
        isHearingImpaired: Boolean = false,
    ) = SavedSubtitle(
        provider = SubtitleProviderKind.WYZIE,
        providerSubtitleId = "wz-1",
        fileName = "x.srt",
        language = language,
        codec = codec,
        isForced = isForced,
        isHearingImpaired = isHearingImpaired,
        fileRelativePath = "wyzie_wz-1.srt",
    )

    private fun stream(
        index: Int,
        language: String? = "eng",
        codec: String? = "subrip",
        isExternal: Boolean = true,
        type: StreamType = StreamType.SUBTITLE,
        isForced: Boolean = false,
        isHearingImpaired: Boolean = false,
    ) = MediaStream(
        index = index,
        type = type,
        language = language,
        codec = codec,
        isExternal = isExternal,
        isForced = isForced,
        isHearingImpaired = isHearingImpaired,
    )

    // ── matchesSavedSubtitle ──────────────────────────────────────────────

    @Test
    fun externalStreamWithMatchingAttributes_matches() {
        assertTrue(stream(7).matchesSavedSubtitle(saved()))
    }

    @Test
    fun embeddedContainerStream_neverMatches() {
        assertFalse(stream(3, isExternal = false).matchesSavedSubtitle(saved()))
    }

    @Test
    fun nonSubtitleStream_neverMatches() {
        assertFalse(stream(2, type = StreamType.AUDIO).matchesSavedSubtitle(saved()))
    }

    @Test
    fun roleFlagMismatch_doesNotMatch() {
        assertFalse(stream(4, isHearingImpaired = true).matchesSavedSubtitle(saved()))
        assertFalse(stream(4, isForced = true).matchesSavedSubtitle(saved()))
    }

    @Test
    fun nullSides_areLenient() {
        // Unknown codec/language on either side must not veto the match.
        assertTrue(stream(5, codec = null).matchesSavedSubtitle(saved(codec = "srt")))
        assertTrue(stream(6, language = "ger").matchesSavedSubtitle(saved(language = null)))
    }

    // ── findSavedSubtitleStreamIndex ──────────────────────────────────────

    @Test
    fun picksFreshlyAppearedMatch_overOlderOnes() {
        val streams = listOf(stream(2), stream(9))
        assertEquals(9, findSavedSubtitleStreamIndex(streams, saved(), preExistingIndices = setOf(2)))
    }

    @Test
    fun noFreshAppearance_fallsBackToHighestIndex() {
        val streams = listOf(stream(2), stream(9))
        assertEquals(9, findSavedSubtitleStreamIndex(streams, saved(), preExistingIndices = setOf(2, 9)))
    }

    @Test
    fun nonMatchingStreams_yieldNull() {
        val streams = listOf(stream(1, language = "ger", isHearingImpaired = true))
        assertNull(findSavedSubtitleStreamIndex(streams, saved()))
    }
}
