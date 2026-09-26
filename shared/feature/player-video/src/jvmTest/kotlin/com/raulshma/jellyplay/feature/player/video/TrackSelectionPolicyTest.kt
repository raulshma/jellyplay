package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RememberedTrack
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

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

    // ─── resolveByStreamIndex: streaming side-load "external:{index}" id tier ──
    //
    // On a transcode the server's text subs are side-loaded (HLS manifest
    // carries none), and buildExternalSubtitles stamps
    // id == "external:{server stream index}" onto each SubtitleSource — both
    // engines propagate it into TrackOption.id. The id is the exact restore
    // key; the label fallback is ambiguous for duplicate-language subs.

    @Test
    fun resolveByStreamIndex_externalIdMatch_beatsAmbiguousLabel() {
        // Two same-language side-loads whose labels do NOT equal the server
        // displayTitle (enrichment missed): only the external: id resolves.
        val tracks = listOf(
            opt(0, "English · text/x-ssa", "eng", id = "external:2"),
            opt(1, "English · Signs", "eng", id = "external:3"),
        )
        val target = MediaStream(index = 3, type = StreamType.SUBTITLE, displayTitle = "English - Ass")
        val match = policy.resolveByStreamIndex(tracks, streamIndex = 3, targetStream = target)
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveByStreamIndex_externalIdMatch_beatsLabelCollision() {
        // The exact external: id outranks the label fallback: when one track's
        // label collides with the target's displayTitle but a different track
        // carries the stored index as its id, the id wins. (A synthetic
        // server track matching only by label stays possible — the helper's
        // selectSubtitleTrack no-op guard neutralizes that pick.)
        val tracks = listOf(
            opt(0, "English - Ass", "eng"),
            opt(1, "English · Signs", "eng", id = "external:3"),
        )
        val target = MediaStream(index = 3, type = StreamType.SUBTITLE, displayTitle = "English - Ass")
        val match = policy.resolveByStreamIndex(tracks, streamIndex = 3, targetStream = target)
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveByStreamIndex_containerIndexStillWinsOverExternalId() {
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 5, id = "external:3"),
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
        preferFullSubtitle: Boolean = false,
        preferSignsSubtitle: Boolean = false,
    ) = SubtitleResolutionArgs(
        tracks, streams, lang, forcedOnly, forced, hearingImpaired, remembered,
        preferFullSubtitle, preferSignsSubtitle,
    )

    private fun audioArgs(
        tracks: List<TrackOption>,
        streams: List<MediaStream> = emptyList(),
        resolvedLang: String = "eng",
        preferAudioDescription: Boolean = false,
        remembered: RememberedTrack? = null,
    ) = AudioResolutionArgs(tracks, streams, resolvedLang, preferAudioDescription, remembered)

    // ─── The remembered-track re-matching ladder ───────────────────────
    //
    // Documented order: stored index (validated in the helper) → language +
    // indexWithinLanguage → language + label (G5 scoring) → language + codec →
    // language-first (matcher) → default. The codec rung rescues label churn.

    @Test
    fun resolveSubtitle_codecRung_rescuesLabelChurn() {
        // The series renamed its sub track so the remembered label no longer
        // substring-overlaps either candidate — the label scores below the
        // confidence threshold, but the codec still says eac3, so the
        // language+codec rung picks the right track.
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", codec = "aac"),
            MediaStream(index = 1, type = StreamType.SUBTITLE, language = "eng", codec = "eac3"),
        )
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 0),
            opt(1, "English (EAC3) Signs", "eng", streamIndex = 1),
        )
        val remembered = RememberedTrack(label = "dialogue subs full track", language = "eng", codec = "eac3")
        val match = policy.resolveSubtitle(subArgs(tracks, streams = streams, lang = "eng", remembered = remembered))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_codecRung_blankLabel_stillMatchesByLanguageAndCodec() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", codec = "aac"),
            MediaStream(index = 1, type = StreamType.SUBTITLE, language = "eng", codec = "eac3"),
        )
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 0),
            opt(1, "English (EAC3)", "eng", streamIndex = 1),
        )
        val remembered = RememberedTrack(label = "", language = "eng", codec = "eac3")
        val match = policy.resolveSubtitle(subArgs(tracks, streams = streams, lang = "eng", remembered = remembered))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_codecRung_codecMissing_fallsThroughToMatcher() {
        // Remembered codec is gone from the re-enumerated list: the rung skips
        // and the matcher's language-first tiebreak (lowest index) applies.
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", codec = "aac"),
            MediaStream(index = 1, type = StreamType.SUBTITLE, language = "eng", codec = "aac"),
        )
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 0),
            opt(1, "English SDH", "eng", streamIndex = 1),
        )
        val remembered = RememberedTrack(label = "Foreign", language = "eng", codec = "eac3")
        val match = policy.resolveSubtitle(subArgs(tracks, streams = streams, lang = "eng", remembered = remembered))
        assertEquals(0, match?.index)
    }

    @Test
    fun resolveAudio_codecRung_rescuesLabelChurn() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO, language = "eng", codec = "aac"),
            MediaStream(index = 1, type = StreamType.AUDIO, language = "eng", codec = "truehd"),
        )
        val tracks = listOf(
            opt(0, "English", "eng", streamIndex = 0),
            opt(1, "English (TrueHD Atmos)", "eng", streamIndex = 1),
        )
        val remembered = RememberedTrack(label = "main feature dub 5.1", language = "eng", codec = "truehd")
        val match = policy.resolveAudio(audioArgs(tracks, streams = streams, resolvedLang = "eng", remembered = remembered))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_languagePlusLabel_scoringHit_beatsCodecRung() {
        // Ladder order: a confident label match wins before the codec rung is
        // ever consulted — even when another track also carries the codec.
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", codec = "eac3"),
            MediaStream(index = 1, type = StreamType.SUBTITLE, language = "eng", codec = "eac3"),
        )
        val tracks = listOf(
            opt(0, "English SDH", "eng", streamIndex = 0),
            opt(1, "English", "eng", streamIndex = 1),
        )
        val remembered = RememberedTrack(label = "English", language = "eng", indexWithinLanguage = 1, codec = "eac3")
        val match = policy.resolveSubtitle(subArgs(tracks, streams = streams, lang = "eng", remembered = remembered))
        assertEquals(1, match?.index)
    }

    @Test
    fun resolveSubtitle_trackWithoutStreamIndex_skipsCodecRung() {
        // Side-loaded subs expose no container index → no codec → the rung
        // cannot fire; the matcher's language-first tiebreak applies.
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.SUBTITLE, language = "eng", codec = "eac3"),
        )
        val tracks = listOf(opt(0, "English", "eng", streamIndex = null))
        val remembered = RememberedTrack(label = "Unmatchable", language = "eng", codec = "eac3")
        val match = policy.resolveSubtitle(subArgs(tracks, streams = streams, lang = "eng", remembered = remembered))
        assertEquals(0, match?.index)
    }

    // ─── Full-dialogue vs signs bias in the matcher ───────────────────

    @Test
    fun resolveSubtitle_preferFull_signsTrackSkippedWhenFullExists() {
        val tracks = listOf(
            opt(0, "Signs & Songs - English", "eng"),
            opt(1, "English", "eng"),
        )
        // Without bias: DEFAULT/lowest-index tiebreak picks the signs track at 0.
        val unbiased = policy.resolveSubtitle(subArgs(tracks, lang = "eng"))
        assertEquals(0, unbiased?.index)
        // With the FULL bias: the full-dialogue track wins the tie.
        val biased = policy.resolveSubtitle(subArgs(tracks, lang = "eng", preferFullSubtitle = true))
        assertEquals(1, biased?.index)
    }

    @Test
    fun resolveSubtitle_preferSigns_signsTrackWinsWhenPresent() {
        val tracks = listOf(
            opt(0, "English", "eng"),
            opt(1, "Signs & Songs - English", "eng"),
        )
        val biased = policy.resolveSubtitle(subArgs(tracks, lang = "eng", preferSignsSubtitle = true))
        assertEquals(1, biased?.index)
    }

    @Test
    fun resolveSubtitle_preferFull_degradesToSignsOnlyTrack() {
        // Only a signs track exists: the bias must not turn the match into Off.
        val tracks = listOf(opt(0, "Signs & Songs - English", "eng"))
        val match = policy.resolveSubtitle(subArgs(tracks, lang = "eng", preferFullSubtitle = true))
        assertEquals(0, match?.index)
    }

    @Test
    fun resolveSubtitle_preferFull_respectsForcedRolesInsideBias() {
        // The bias reruns the role tiers on the preferred subset: a forced pin
        // still only matches forced tracks, biased or not.
        val tracks = listOf(
            opt(0, "English", "eng", badges = listOf(TrackBadge.DEFAULT)),
            opt(1, "English Forced", "eng", badges = listOf(TrackBadge.FORCED)),
        )
        val match = policy.resolveSubtitle(
            subArgs(tracks, lang = "eng", forced = true, preferFullSubtitle = true),
        )
        assertEquals(1, match?.index)
    }
}
