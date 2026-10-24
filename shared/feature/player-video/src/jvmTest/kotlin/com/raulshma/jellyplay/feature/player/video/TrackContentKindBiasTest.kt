package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.LanguageRule
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.RuleContentType
import com.raulshma.jellyplay.core.model.SubtitleContentKindFilter
import com.raulshma.jellyplay.core.model.SubtitleTrackMode
import com.raulshma.jellyplay.core.model.TrackSelectionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The track-rule engine v2 (issue #23): the per-rule content-kind +
 * commentary dimensions, end to end — rule → [TrackResolutionEngine.resolve]
 * emission → [TrackSelectionPolicy]/[SubtitleTrackMatcher] tier bias. The
 * required verdicts: a signs-only rule picks the signs track over the full
 * one; a full rule ignores signs; commentary tracks rank last by default.
 */
class TrackContentKindBiasTest {

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun opt(index: Int, label: String, badges: List<com.raulshma.jellyplay.feature.player.video.engine.TrackBadge> = emptyList()) =
        TrackOption(index = index, label = label, language = "eng", isSelected = false, badges = badges)

    private fun ruleSet(
        contentKind: SubtitleContentKindFilter,
        commentary: Boolean = false,
        mode: SubtitleTrackMode = SubtitleTrackMode.FULL,
    ) = LanguageRuleSet(
        preset = TrackSelectionPreset.CUSTOM,
        rules = listOf(
            LanguageRule(
                id = "r1",
                appliesTo = RuleContentType.ALL,
                subtitleLanguages = listOf("eng"),
                subtitleMode = mode,
                subtitleContentKind = contentKind,
                commentary = commentary,
            ),
        ),
    )

    private val ctx = TrackRuleContext(
        contentType = RuleContentType.MOVIE,
        titles = listOf("Some Movie"),
    )

    // ── Resolution emission ─────────────────────────────────────────────────

    @Test
    fun `matched rule carries its content kind and commentary opt-in`() {
        val resolution = TrackResolutionEngine.resolve(ruleSet(SubtitleContentKindFilter.SIGNS_SONGS), ctx)
        assertEquals(SubtitleContentKindFilter.SIGNS_SONGS, resolution?.subtitleContentKind)
        assertFalse(resolution?.allowCommentary == true, "commentary=false (default) folds through")
    }

    @Test
    fun `commentary opt-in rule allows commentary`() {
        val resolution = TrackResolutionEngine.resolve(
            ruleSet(SubtitleContentKindFilter.ANY, commentary = true),
            ctx,
        )
        assertEquals(true, resolution?.allowCommentary)
    }

    @Test
    fun `preset and global rungs carry the neutral v2 defaults`() {
        // Rung 2 (structural preset): no rule matched — no kind, no bias.
        val preset = TrackResolutionEngine.resolve(
            LanguageRuleSet(preset = TrackSelectionPreset.SUBBED_ALL),
            TrackRuleContext(RuleContentType.MOVIE, listOf("Movie")),
        )
        assertEquals(SubtitleContentKindFilter.ANY, preset?.subtitleContentKind)
        assertEquals(true, preset?.allowCommentary)
        // Rung 3 (global languages): same.
        val global = TrackResolutionEngine.resolve(
            LanguageRuleSet(
                preset = TrackSelectionPreset.CUSTOM,
                subtitleLanguages = listOf("eng"),
            ),
            ctx,
        )
        assertEquals(SubtitleContentKindFilter.ANY, global?.subtitleContentKind)
        assertEquals(true, global?.allowCommentary)
    }

    // ── Matcher-level bias (the required verdicts) ──────────────────────────

    private val fullTrack = opt(0, "English")
    private val signsTrack = opt(1, "English (Signs & Songs)")
    private val commentaryTrack = opt(2, "English (Commentary)")

    @Test
    fun `signs-only rule picks the signs track over the full one`() {
        val tracks = listOf(fullTrack, signsTrack)
        val match = SubtitleTrackMatcher.match(
            tracks, "eng", forced = null, hearingImpaired = null,
            contentKind = SubtitleContentKindFilter.SIGNS_SONGS,
        )
        assertEquals(signsTrack.index, match?.index)
    }

    @Test
    fun `full rule ignores signs`() {
        val tracks = listOf(signsTrack, fullTrack)
        val match = SubtitleTrackMatcher.match(
            tracks, "eng", forced = null, hearingImpaired = null,
            contentKind = SubtitleContentKindFilter.FULL,
        )
        assertEquals(fullTrack.index, match?.index)
    }

    @Test
    fun `commentary ranks last by default`() {
        val tracks = listOf(commentaryTrack, fullTrack)
        val match = SubtitleTrackMatcher.match(
            tracks, "eng", forced = null, hearingImpaired = null,
            allowCommentary = false,
        )
        assertEquals(fullTrack.index, match?.index)
    }

    @Test
    fun `commentary-only item still resolves to the commentary track`() {
        // The bias is a preference, not a filter — the tier ladder falls back.
        val match = SubtitleTrackMatcher.match(
            listOf(commentaryTrack), "eng", forced = null, hearingImpaired = null,
            allowCommentary = false,
        )
        assertEquals(commentaryTrack.index, match?.index)
    }

    @Test
    fun `exclude-commentary kind keeps non-commentary ahead`() {
        val match = SubtitleTrackMatcher.match(
            listOf(commentaryTrack, signsTrack), "eng", forced = null, hearingImpaired = null,
            contentKind = SubtitleContentKindFilter.EXCLUDE_COMMENTARY,
        )
        assertEquals(signsTrack.index, match?.index)
    }

    @Test
    fun `unknown-title tracks still resolve under every kind target via the tier fallback`() {
        // kindSatisfies is STRICT (unknown titles satisfy only ANY and
        // EXCLUDE_COMMENTARY directly), but the bias is never a filter: with
        // nothing satisfying, the ladder falls back and resolves anyway.
        val unknown = opt(3, "English")
        for (kind in SubtitleContentKindFilter.entries) {
            val match = SubtitleTrackMatcher.match(
                listOf(unknown), "eng", forced = null, hearingImpaired = null,
                contentKind = kind,
            )
            assertEquals(unknown.index, match?.index, "kind=$kind")
        }
    }

    @Test
    fun `signs rule prefers a signs title over an unclassifiable one`() {
        // Plain "English" carries no markers (UNKNOWN): the SIGNS_SONGS rule
        // must not let it win the tier on the lowest-index tiebreak.
        val unknown = opt(0, "English")
        val match = SubtitleTrackMatcher.match(
            listOf(unknown, signsTrack), "eng", forced = null, hearingImpaired = null,
            contentKind = SubtitleContentKindFilter.SIGNS_SONGS,
        )
        assertEquals(signsTrack.index, match?.index)
    }

    @Test
    fun `v2 bias falls back when no candidate satisfies`() {
        // A SIGNS_SONGS rule over a full-only item still resolves (never Off).
        val match = SubtitleTrackMatcher.match(
            listOf(fullTrack), "eng", forced = null, hearingImpaired = null,
            contentKind = SubtitleContentKindFilter.SIGNS_SONGS,
        )
        assertEquals(fullTrack.index, match?.index)
    }

    @Test
    fun `defaults keep the unbiased legacy behavior`() {
        // ANY + commentary-allowed (the default args) = the pre-v2 tiebreak:
        // DEFAULT badge, then lowest index.
        val defaultBadged = opt(4, "English", badges = listOf(com.raulshma.jellyplay.feature.player.video.engine.TrackBadge.DEFAULT))
        val match = SubtitleTrackMatcher.match(
            listOf(signsTrack, defaultBadged), "eng", forced = null, hearingImpaired = null,
        )
        assertEquals(defaultBadged.index, match?.index)
    }

    // ── Policy plumbing ─────────────────────────────────────────────────────

    @Test
    fun `policy routes the v2 dimensions into the matcher`() {
        val tracks = listOf(fullTrack, signsTrack)
        val match = TrackSelectionPolicy().resolveSubtitle(
            SubtitleResolutionArgs(
                tracks = tracks,
                streams = emptyList(),
                lang = "eng",
                forcedOnly = false,
                forced = null,
                hearingImpaired = null,
                remembered = null,
                contentKind = SubtitleContentKindFilter.SIGNS_SONGS,
                allowCommentary = false,
            ),
        )
        assertEquals(signsTrack.index, match?.index)
    }

    @Test
    fun `policy defaults stay unbiased`() {
        val defaultBadged = opt(4, "English", badges = listOf(com.raulshma.jellyplay.feature.player.video.engine.TrackBadge.DEFAULT))
        val match = TrackSelectionPolicy().resolveSubtitle(
            SubtitleResolutionArgs(
                tracks = listOf(signsTrack, defaultBadged),
                streams = emptyList(),
                lang = "eng",
                forcedOnly = false,
                forced = null,
                hearingImpaired = null,
                remembered = null,
            ),
        )
        assertEquals(defaultBadged.index, match?.index)
    }

    // ── Forced-only gating is untouched ─────────────────────────────────────

    @Test
    fun `forced-only path still wins over the v2 bias`() {
        val forced = opt(5, "English Forced", badges = listOf(com.raulshma.jellyplay.feature.player.video.engine.TrackBadge.FORCED))
        val forcedStream = com.raulshma.jellyplay.core.model.MediaStream(
            index = 7,
            type = com.raulshma.jellyplay.core.model.StreamType.SUBTITLE,
            language = "eng",
            displayTitle = "English Forced",
            isForced = true,
        )
        val policy = TrackSelectionPolicy()
        val match = policy.resolveSubtitle(
            SubtitleResolutionArgs(
                tracks = listOf(fullTrack, signsTrack, forced),
                streams = listOf(forcedStream),
                lang = "eng",
                forcedOnly = true,
                forced = null,
                hearingImpaired = null,
                remembered = null,
                contentKind = SubtitleContentKindFilter.SIGNS_SONGS,
            ),
        )
        // Forced-only never reaches the tiered matcher: the forced stream
        // pick stands (accessibility intent outranks the class bias).
        assertEquals(forced.index, match?.index)
    }

    @Test
    fun `forced-only with no forced track returns null`() {
        assertNull(
            TrackSelectionPolicy().resolveSubtitle(
                SubtitleResolutionArgs(
                    tracks = listOf(fullTrack),
                    streams = emptyList(),
                    lang = "eng",
                    forcedOnly = true,
                    forced = null,
                    hearingImpaired = null,
                    remembered = null,
                ),
            ),
        )
    }
}
