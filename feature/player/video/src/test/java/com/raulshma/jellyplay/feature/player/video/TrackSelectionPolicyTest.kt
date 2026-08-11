package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the track-selection *policy* — the precedence ladder that used
 * to live only as a call sequence inside `TrackSelectionHelper` and was therefore
 * untestable as a unit (the legacy `TrackSelectionLogicTest` re-implemented the
 * logic with local vars instead of calling real code).
 *
 * Pure data in/out — no engine, no coroutines, no mockk.
 */
class TrackSelectionPolicyTest {

    private val policy = TrackSelectionPolicy()

    // ─── resolveSubtitle: ordering & forced-only ──────────────────────────────

    @Test
    fun resolveSubtitle_noTracks_returnsNull() {
        val match = policy.resolveSubtitle(
            SubtitleResolutionArgs(
                tracks = emptyList(),
                streams = emptyList(),
                lang = "eng",
                forcedOnly = false,
                forced = null,
                hearingImpaired = null,
                remembered = null,
            ),
        )
        assertNull(match)
    }

    @Test
    fun resolveSubtitle_noSameLanguageTrack_returnsNull() {
        val tracks = listOf(
            opt(0, "Spanish", "spa"),
            opt(1, "French", "fra"),
        )
        val match = policy.resolveSubtitle(subArgs(tracks, lang = "eng"))
        assertNull(match)
    }

    @Test
    fun resolveSubtitle_languageMatch_picksSameLanguageTrack() {
        val tracks = listOf(
            opt(0, "Spanish", "spa"),
            opt(1, "English", "eng"),
        )
        val match = policy.resolveSubtitle(subArgs(tracks, lang = "eng"))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_defaultBadgeWinsTiebreak() {
        // Two English tracks; the DEFAULT-badged one wins the deterministic tiebreak.
        val tracks = listOf(
            opt(0, "English", "eng"),
            opt(1, "English", "eng", badges = listOf(TrackBadge.DEFAULT)),
        )
        val match = policy.resolveSubtitle(subArgs(tracks, lang = "eng"))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_forcedOnly_picksForcedStream() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", displayTitle = "English", isForced = false),
            MediaStream(index = 1, type = StreamType.SUBTITLE, language = "eng", displayTitle = "English Forced", isForced = true),
        )
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 0),
            opt(1, "English Forced", "eng", streamIndex = 1),
        )
        val match = policy.resolveSubtitle(
            subArgs(tracks, streams = streams, lang = "eng", forcedOnly = true),
        )
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_forcedOnly_bypassesScoring_forAccessibility() {
        // Scoring would carry the remembered plain track forward; forced-only must
        // ignore it so the forced track wins regardless.
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", displayTitle = "English", isForced = false),
            MediaStream(index = 1, type = StreamType.SUBTITLE, language = "eng", displayTitle = "English Forced", isForced = true),
        )
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 0),
            opt(1, "English Forced", "eng", streamIndex = 1),
        )
        val remembered = RememberedTrack(label = "English", language = "eng", indexWithinLanguage = 0)
        val match = policy.resolveSubtitle(
            subArgs(tracks, streams = streams, lang = "eng", forcedOnly = true, remembered = remembered),
        )
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_scoringConfidentMatch_beatsLanguageMatch() {
        // Two English tracks; a remembered specific "English · 5.1" (which was the
        // 2nd English track in the prior episode) scores highest on its twin and
        // must win over the bare first-English pick. The exact-label match (+2)
        // plus positional-index agreement (+1) beats the generic track's weaker
        // substring-only overlap, so the specific pick carries forward.
        val tracks = listOf(
            opt(0, "English", "eng"),
            opt(1, "English · 5.1", "eng"),
        )
        val remembered = RememberedTrack(label = "English · 5.1", language = "eng", indexWithinLanguage = 1)
        val match = policy.resolveSubtitle(subArgs(tracks, lang = "eng", remembered = remembered))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_scoringLowScore_fallsThroughToLanguageRule() {
        // Remembered label shares nothing with either candidate → score < 3 → falls
        // through to SubtitleTrackMatcher, which returns the first same-language track.
        val tracks = listOf(
            opt(0, "English", "eng"),
            opt(1, "English Commentary", "eng"),
        )
        val remembered = RememberedTrack(label = "Spanish · Castilian", language = "spa", indexWithinLanguage = 0)
        val match = policy.resolveSubtitle(subArgs(tracks, lang = "eng", remembered = remembered))
        // Matcher's tiebreak prefers DEFAULT, else lowest index → 0.
        assertEquals(0, match?.index)
    }

    @Test
    fun resolveSubtitle_rememberedBlankLabel_ignoredFallsToMatcher() {
        val tracks = listOf(opt(0, "English", "eng"))
        val remembered = RememberedTrack(label = "", language = "eng")
        val match = policy.resolveSubtitle(subArgs(tracks, lang = "eng", remembered = remembered))
        assertEquals(0, match?.index)
    }

    // ─── resolveAudio: ordering & audio-description ───────────────────────────

    @Test
    fun resolveAudio_languageMatch_picksSameLanguageTrack() {
        val tracks = listOf(
            opt(0, "Spanish", "spa"),
            opt(1, "English", "eng"),
        )
        val match = policy.resolveAudio(audioArgs(tracks, resolvedLang = "eng"))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveAudio_noSelectableTrack_returnsNull() {
        val tracks = listOf(opt(-1, "Default", null))
        val match = policy.resolveAudio(audioArgs(tracks, resolvedLang = "eng"))
        assertNull(match)
    }

    @Test
    fun resolveAudio_preferAudioDescription_picksDescriptiveTrack() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO, displayTitle = "English"),
            MediaStream(index = 1, type = StreamType.AUDIO, displayTitle = "English Audio Description"),
        )
        val tracks = listOf(
            opt(0, "English", "eng"),
            opt(1, "English Audio Description", "eng"),
        )
        val match = policy.resolveAudio(
            audioArgs(tracks, streams = streams, resolvedLang = "eng", preferAudioDescription = true),
        )
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveAudio_scoringConfidentMatch_beatsLanguageMatch() {
        val tracks = listOf(
            opt(0, "English", "eng"),
            opt(1, "English · 5.1 · DTS", "eng"),
        )
        val remembered = RememberedTrack(label = "English · 5.1 · DTS", language = "eng", indexWithinLanguage = 1)
        val match = policy.resolveAudio(audioArgs(tracks, resolvedLang = "eng", remembered = remembered))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveAudio_noLanguageMatch_returnsNull() {
        val tracks = listOf(opt(0, "Spanish", "spa"))
        val match = policy.resolveAudio(audioArgs(tracks, resolvedLang = "eng"))
        assertNull(match)
    }

    // ─── resolveByStreamIndex: container-index then label tiers ──────────────

    @Test
    fun resolveByStreamIndex_containerIndexMatch_wins() {
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 5),
        )
        val target = MediaStream(index = 5, type = StreamType.SUBTITLE, displayTitle = "English")
        val match = policy.resolveByStreamIndex(tracks, streamIndex = 5, targetStream = target)
        assertEquals(0, match?.index)
    }

    @Test
    fun resolveByStreamIndex_noContainerIndex_fallsBackToLabel() {
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = null),
        )
        val target = MediaStream(index = 5, type = StreamType.SUBTITLE, displayTitle = "English")
        val match = policy.resolveByStreamIndex(tracks, streamIndex = 5, targetStream = target)
        assertEquals(0, match?.index)
    }

    @Test
    fun resolveByStreamIndex_noMatch_returnsNull() {
        val tracks = listOf(opt(0, "Spanish", "spa", streamIndex = 1))
        val target = MediaStream(index = 5, type = StreamType.SUBTITLE, displayTitle = "English")
        val match = policy.resolveByStreamIndex(tracks, streamIndex = 5, targetStream = target)
        assertNull(match)
    }

    @Test
    fun resolveByStreamIndex_nullTargetStream_noLabel_returnsNull() {
        val tracks = listOf(opt(0, "English", "eng", streamIndex = null))
        val target = MediaStream(index = 5, type = StreamType.SUBTITLE, language = null, displayTitle = null, title = null)
        val match = policy.resolveByStreamIndex(tracks, streamIndex = 5, targetStream = target)
        assertNull(match)
    }

    @Test
    fun resolveByStreamIndex_ignoresPlaceholderTracks() {
        // The Off/Default placeholder has index < 0 and must never match.
        val tracks = listOf(
            opt(-1, "Off", null),
            opt(0, "English", "eng", streamIndex = 5),
        )
        val target = MediaStream(index = 5, type = StreamType.SUBTITLE, displayTitle = "English")
        val match = policy.resolveByStreamIndex(tracks, streamIndex = 5, targetStream = target)
        assertEquals(0, match?.index)
    }

    // ─── resolveMediaStreamIndex: offline persistence path ────────────────────

    @Test
    fun resolveMediaStreamIndex_emptyStreams_offline_returnsEngineIndex() {
        // Offline (no server streams) persists the engine positional index directly.
        val option = opt(0, "English", "eng")
        val result = policy.resolveMediaStreamIndex(streams = emptyList(), type = StreamType.SUBTITLE, trackOption = option)
        assertEquals(0, result)
    }

    @Test
    fun resolveMediaStreamIndex_exactLabelMatch_returnsStreamIndex() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO, displayTitle = "English"),
            MediaStream(index = 1, type = StreamType.AUDIO, displayTitle = "Spanish"),
        )
        val option = opt(0, "English", "eng")
        val result = policy.resolveMediaStreamIndex(streams, StreamType.AUDIO, option)
        assertEquals(0, result)
    }

    @Test
    fun resolveMediaStreamIndex_languageMatchOnly_returnsBestLanguageMatch() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO, language = "eng", displayTitle = "Surround"),
            MediaStream(index = 1, type = StreamType.AUDIO, language = "eng", displayTitle = "Stereo", isDefault = true),
        )
        val option = opt(0, "English · 5.1", "eng")
        // No exact label match; two language matches → DEFAULT-badged wins (index 1).
        val result = policy.resolveMediaStreamIndex(streams, StreamType.AUDIO, option)
        assertEquals(1, result)
    }

    // ─── resolveByOfflineSubtitleId: offline side-loaded subtitle restore ─────
    //
    // Offline playback carries no server MediaStreams, so the only stable handle
    // linking the detail selector's persisted server-stream index to an engine
    // track is the `"offline:${index}"` id stamped onto the SubtitleSource.

    @Test
    fun resolveByOfflineSubtitleId_matchesIdEqualToOfflineIndex() {
        // The detail selector stored the original server stream index (2) as
        // subtitleStreamIndex; the side-loaded sub carries it as id == "offline:2".
        val tracks = listOf(
            opt(-1, "Off", null),
            opt(0, "English", "eng", id = "offline:0"),
            opt(1, "Spanish", "spa", id = "offline:2"),
        )
        val match = policy.resolveByOfflineSubtitleId(tracks, index = 2)
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveByOfflineSubtitleId_noMatchingId_returnsNull() {
        val tracks = listOf(opt(0, "English", "eng", id = "offline:0"))
        assertNull(policy.resolveByOfflineSubtitleId(tracks, index = 2))
    }

    @Test
    fun resolveByOfflineSubtitleId_ignoresPlaceholderAndNonOfflineIds() {
        // The Off placeholder (index < 0) and tracks whose id follows a different
        // contract (mpv synthetic, remote external) must never match.
        val tracks = listOf(
            opt(-1, "Off", null, id = "offline:2"), // placeholder — index < 0
            opt(0, "English", "eng", id = "mpv_sub_2"),
            opt(1, "Spanish", "spa", id = "external:2"),
        )
        assertNull(policy.resolveByOfflineSubtitleId(tracks, index = 2))
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun opt(
        index: Int,
        label: String,
        language: String?,
        streamIndex: Int? = null,
        badges: List<TrackBadge> = emptyList(),
        id: String? = null,
    ) = TrackOption(index, label, language, isSelected = false, streamIndex = streamIndex, badges = badges, id = id)

    private fun subArgs(
        tracks: List<TrackOption>,
        streams: List<MediaStream> = emptyList(),
        lang: String = "eng",
        forcedOnly: Boolean = false,
        forced: Boolean? = null,
        hearingImpaired: Boolean? = null,
        remembered: RememberedTrack? = null,
    ) = SubtitleResolutionArgs(tracks, streams, lang, forcedOnly, forced, hearingImpaired, remembered)

    private fun audioArgs(
        tracks: List<TrackOption>,
        streams: List<MediaStream> = emptyList(),
        resolvedLang: String = "eng",
        preferAudioDescription: Boolean = false,
        remembered: RememberedTrack? = null,
    ) = AudioResolutionArgs(tracks, streams, resolvedLang, preferAudioDescription, remembered)
}
