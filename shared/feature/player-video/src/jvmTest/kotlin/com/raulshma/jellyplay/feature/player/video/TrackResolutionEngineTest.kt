package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.LanguageRule
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RuleContentType
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.model.SubtitleTrackMode
import com.raulshma.jellyplay.core.model.TrackSelectionPreset
import com.raulshma.jellyplay.core.model.mediaRuleContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exhaustive matrix for the rule engine: preset semantics, signs-vs-full
 * classification, allow-lists, rule gating and rung order, and the
 * byte-identical passthrough contract for the identity (empty/MANUAL) set.
 */
class TrackResolutionEngineTest {

    // ─── isSignsTrack classification ───────────────────────────────────────

    @Test
    fun `isSignsTrack matches the title regex case-insensitively`() {
        for (title in listOf(
            "Signs & Songs - English",
            "song track",
            "Songs Track",
            "Opening",
            "ED - English",
            "OP",
            "Lyrics",
            "Credits",
            "sign",
        )) {
            assertTrue(
                TrackResolutionEngine.isSignsTrack(stream(type = StreamType.SUBTITLE, displayTitle = title)),
                "expected '$title' to classify as signs/songs",
            )
        }
    }

    @Test
    fun `isSignsTrack does not match full-dialogue titles or word fragments`() {
        for (title in listOf("English", "English SDH", "Japanese", "Dialogue", "Legendary", "Trending", "Editor")) {
            assertFalse(
                TrackResolutionEngine.isSignsTrack(stream(type = StreamType.SUBTITLE, displayTitle = title)),
                "expected '$title' to classify as full dialogue",
            )
        }
    }

    @Test
    fun `isSignsTrack forced stream classifies as signs even without a title`() {
        assertTrue(TrackResolutionEngine.isSignsTrack(stream(type = StreamType.SUBTITLE, isForced = true)))
    }

    @Test
    fun `isSignsTrack blank title without forced flag is full`() {
        assertFalse(TrackResolutionEngine.isSignsTrack(stream(type = StreamType.SUBTITLE)))
    }

    @Test
    fun `isSignsTrack option-level variant reads label and FORCED badge`() {
        assertTrue(
            TrackResolutionEngine.isSignsTrack(
                TrackOption(index = 0, label = "Signs & Songs - English", language = "eng", isSelected = false),
            ),
        )
        assertTrue(
            TrackResolutionEngine.isSignsTrack(
                TrackOption(index = 0, label = "English", language = "eng", isSelected = false, badges = badgeList()),
            ),
        )
        assertFalse(
            TrackResolutionEngine.isSignsTrack(
                TrackOption(index = 0, label = "English", language = "eng", isSelected = false),
            ),
        )
    }

    // ─── Identity / passthrough contract ────────────────────────────────────

    @Test
    fun `identity rule set resolves to null`() {
        assertNull(TrackResolutionEngine.resolve(LanguageRuleSet(), episodeContext))
        // Explicitly MANUAL with content of every type, with and without rules.
        assertNull(
            TrackResolutionEngine.resolve(
                LanguageRuleSet(preset = TrackSelectionPreset.MANUAL),
                episodeContext,
            ),
        )
        assertFalse(LanguageRuleSet().isActive)
    }

    @Test
    fun `manual preset ignores rules and languages`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.MANUAL,
            audioLanguages = listOf("jpn"),
            subtitleLanguages = listOf("eng"),
            rules = listOf(rule(pattern = null, audio = listOf("jpn"))),
        )
        assertNull(TrackResolutionEngine.resolve(rules, episodeContext))
    }

    // ─── Preset matrix ──────────────────────────────────────────────────────

    @Test
    fun `subbed_shows - episodes want subtitles with the configured subtitle language`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.SUBBED_SHOWS,
            subtitleLanguages = listOf("eng"),
            audioLanguages = listOf("jpn"), // must NOT drive the audio axis
        )
        val resolution = TrackResolutionEngine.resolve(rules, episodeContext)
        assertEquals("eng", resolution?.subtitleLanguage)
        assertNull(resolution?.audioLanguage)
        assertEquals(SubtitleTrackMode.FULL, resolution?.subtitleMode)
        assertFalse(resolution?.subtitleDisabled == true)
    }

    @Test
    fun `subbed_shows - movies get no preset opinion`() {
        // No configured languages: a movie on a shows-scoped preset resolves
        // to nothing — the existing ladder applies untouched.
        val rules = LanguageRuleSet(preset = TrackSelectionPreset.SUBBED_SHOWS)
        assertNull(TrackResolutionEngine.resolve(rules, movieContext))
        // With global rule-set languages configured, the movie still picks
        // them up — via the GLOBAL rung (plan rung order), not the preset.
        val withLanguages = LanguageRuleSet(
            preset = TrackSelectionPreset.SUBBED_SHOWS,
            subtitleLanguages = listOf("eng"),
        )
        val viaGlobal = TrackResolutionEngine.resolve(withLanguages, movieContext)
        assertEquals("eng", viaGlobal?.subtitleLanguage)
        assertNull(viaGlobal?.audioLanguage)
    }

    @Test
    fun `dubbed_shows - episodes want dubbed audio with subtitles off`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.DUBBED_SHOWS,
            audioLanguages = listOf("ger"),
        )
        val resolution = TrackResolutionEngine.resolve(rules, episodeContext)
        assertEquals("ger", resolution?.audioLanguage)
        assertTrue(resolution?.subtitleDisabled == true)
        assertEquals(SubtitleTrackMode.OFF, resolution?.subtitleMode)
    }

    @Test
    fun `dubbed_shows - movies get no preset opinion`() {
        val rules = LanguageRuleSet(preset = TrackSelectionPreset.DUBBED_SHOWS)
        assertNull(TrackResolutionEngine.resolve(rules, movieContext))
        // Global rule-set audio languages still reach the movie via the
        // global rung — but the preset's subtitles-off does not.
        val withLanguages = LanguageRuleSet(
            preset = TrackSelectionPreset.DUBBED_SHOWS,
            audioLanguages = listOf("ger"),
        )
        val viaGlobal = TrackResolutionEngine.resolve(withLanguages, movieContext)
        assertEquals("ger", viaGlobal?.audioLanguage)
        assertFalse(viaGlobal?.subtitleDisabled == true)
    }

    @Test
    fun `subbed_all and dubbed_all apply to movies and episodes`() {
        val subbed = LanguageRuleSet(preset = TrackSelectionPreset.SUBBED_ALL, subtitleLanguages = listOf("eng"))
        assertEquals("eng", TrackResolutionEngine.resolve(subbed, movieContext)?.subtitleLanguage)
        assertEquals("eng", TrackResolutionEngine.resolve(subbed, episodeContext)?.subtitleLanguage)

        val dubbed = LanguageRuleSet(preset = TrackSelectionPreset.DUBBED_ALL, audioLanguages = listOf("ger"))
        assertTrue(TrackResolutionEngine.resolve(dubbed, movieContext)?.subtitleDisabled == true)
        assertEquals("ger", TrackResolutionEngine.resolve(dubbed, episodeContext)?.audioLanguage)
    }

    @Test
    fun `subbed preset without configured languages still yields a full-mode opinion`() {
        // The headline "subbed shows" use case with no language lists: the
        // preset still expresses "subtitles wanted" (mode FULL) while leaving
        // the language to the global preferred rung.
        val resolution = TrackResolutionEngine.resolve(
            LanguageRuleSet(preset = TrackSelectionPreset.SUBBED_SHOWS),
            episodeContext,
        )
        assertEquals(SubtitleTrackMode.FULL, resolution?.subtitleMode)
        assertNull(resolution?.subtitleLanguage)
    }

    @Test
    fun `custom preset alone yields no opinion`() {
        assertNull(TrackResolutionEngine.resolve(LanguageRuleSet(preset = TrackSelectionPreset.CUSTOM), episodeContext))
    }

    // ─── Rule matching: order, appliesTo, title pattern ─────────────────────

    @Test
    fun `first matching rule wins over later ones and over presets`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.DUBBED_ALL,
            rules = listOf(
                rule(id = "first", pattern = "Cowboy", audio = listOf("jpn"), subtitle = listOf("eng")),
                rule(id = "second", pattern = "Cowboy", audio = listOf("fra"), subtitle = listOf("deu")),
            ),
        )
        val resolution = TrackResolutionEngine.resolve(rules, context(titles = listOf("Cowboy Bebop")))
        assertEquals("jpn", resolution?.audioLanguage)
        assertEquals("eng", resolution?.subtitleLanguage)
    }

    @Test
    fun `non-matching rule falls through to the next one`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(
                rule(id = "other", pattern = "Bebop", audio = listOf("jpn")),
                rule(id = "matching", pattern = "trigun", audio = listOf("ger")),
            ),
        )
        // "trigun" matches case-insensitively even though the context title is "TRIGUN".
        val resolution = TrackResolutionEngine.resolve(rules, context(titles = listOf("TRIGUN")))
        assertEquals("ger", resolution?.audioLanguage)
    }

    @Test
    fun `rule appliesTo gates by content type`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(
                LanguageRule(id = "movies-only", appliesTo = RuleContentType.MOVIE, audioLanguages = listOf("ger")),
                LanguageRule(id = "episodes-only", appliesTo = RuleContentType.EPISODE, subtitleLanguages = listOf("eng")),
            ),
        )
        assertEquals("ger", TrackResolutionEngine.resolve(rules, movieContext)?.audioLanguage)
        assertNull(TrackResolutionEngine.resolve(rules, movieContext)?.subtitleLanguage)
        assertEquals("eng", TrackResolutionEngine.resolve(rules, episodeContext)?.subtitleLanguage)
        assertNull(TrackResolutionEngine.resolve(rules, episodeContext)?.audioLanguage)
    }

    @Test
    fun `rule with ALL appliesTo matches both content types`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(rule(id = "any", appliesTo = RuleContentType.ALL, audio = listOf("jpn"))),
        )
        assertEquals("jpn", TrackResolutionEngine.resolve(rules, movieContext)?.audioLanguage)
        assertEquals("jpn", TrackResolutionEngine.resolve(rules, episodeContext)?.audioLanguage)
    }

    @Test
    fun `rule title pattern matches series OR item name`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(rule(id = "series", pattern = "One Piece", subtitle = listOf("eng"))),
        )
        // Series name matches even when the episode name does not.
        val bySeries = TrackResolutionEngine.resolve(
            rules,
            context(titles = listOf("One Piece", "Romance Dawn")),
        )
        assertEquals("eng", bySeries?.subtitleLanguage)
        // A rule pattern can also target the item name alone.
        val byItem = TrackResolutionEngine.resolve(
            LanguageRuleSet(
                preset = TrackSelectionPreset.CUSTOM,
                rules = listOf(rule(id = "item", pattern = "Romance Dawn", subtitle = listOf("jpn"))),
            ),
            context(titles = listOf("One Piece", "Romance Dawn")),
        )
        assertEquals("jpn", byItem?.subtitleLanguage)
    }

    @Test
    fun `rule without a pattern matches by content type alone`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(rule(id = "blank", pattern = "   ", audio = listOf("ger"))),
        )
        assertEquals("ger", TrackResolutionEngine.resolve(rules, episodeContext)?.audioLanguage)
    }

    @Test
    fun `corrupt rule pattern never matches instead of throwing`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(rule(id = "bad", pattern = "([unclosed", audio = listOf("ger"))),
        )
        assertNull(TrackResolutionEngine.resolve(rules, episodeContext))
    }

    @Test
    fun `rule subtitle modes carry through - forced only and off`() {
        val forced = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(
                LanguageRule(
                    id = "forced",
                    subtitleLanguages = listOf("eng"),
                    subtitleMode = SubtitleTrackMode.FORCED_ONLY,
                ),
            ),
        )
        val forcedResolution = TrackResolutionEngine.resolve(forced, episodeContext)
        assertEquals(SubtitleTrackMode.FORCED_ONLY, forcedResolution?.subtitleMode)
        assertFalse(forcedResolution?.subtitleDisabled == true)

        val off = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(
                LanguageRule(id = "off", audioLanguages = listOf("ger"), subtitleMode = SubtitleTrackMode.OFF),
            ),
        )
        val offResolution = TrackResolutionEngine.resolve(off, episodeContext)
        assertTrue(offResolution?.subtitleDisabled == true)
    }

    @Test
    fun `full_prefer_signs mode carries through without disabling`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            rules = listOf(
                LanguageRule(
                    id = "signs",
                    subtitleLanguages = listOf("eng"),
                    subtitleMode = SubtitleTrackMode.FULL_PREFER_SIGNS,
                ),
            ),
        )
        val resolution = TrackResolutionEngine.resolve(rules, episodeContext)
        assertEquals(SubtitleTrackMode.FULL_PREFER_SIGNS, resolution?.subtitleMode)
        assertFalse(resolution?.subtitleDisabled == true)
        assertEquals("eng", resolution?.subtitleLanguage)
    }

    // ─── Global rule-set languages rung ─────────────────────────────────────

    @Test
    fun `global rule-set languages apply when no rule or preset opinion exists`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            audioLanguages = listOf("jpn", "ger"),
            subtitleLanguages = listOf("eng"),
        )
        val resolution = TrackResolutionEngine.resolve(rules, movieContext)
        assertEquals("jpn", resolution?.audioLanguage)
        assertEquals("eng", resolution?.subtitleLanguage)
        assertEquals(SubtitleTrackMode.FULL, resolution?.subtitleMode)
    }

    @Test
    fun `allow-lists alone keep the rule set active without producing a resolution`() {
        val rules = LanguageRuleSet(
            preset = TrackSelectionPreset.CUSTOM,
            subtitleAllowList = setOf("eng", "jpn"),
        )
        assertTrue(rules.isActive)
        assertTrue(rules.hasAllowList)
        // No language/mode opinion — the allow-list acts at the ladder level.
        assertNull(TrackResolutionEngine.resolve(rules, episodeContext))
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private val episodeContext = TrackRuleContext(
        contentType = RuleContentType.EPISODE,
        titles = listOf("Some Series", "Some Episode"),
    )
    private val movieContext = TrackRuleContext(
        contentType = RuleContentType.MOVIE,
        titles = listOf("Some Movie"),
    )

    private fun context(titles: List<String>, type: RuleContentType = RuleContentType.EPISODE) =
        TrackRuleContext(contentType = type, titles = titles)

    private fun rule(
        id: String = "rule",
        appliesTo: RuleContentType = RuleContentType.ALL,
        pattern: String? = null,
        audio: List<String> = emptyList(),
        subtitle: List<String> = emptyList(),
        mode: SubtitleTrackMode = SubtitleTrackMode.FULL,
    ) = LanguageRule(
        id = id,
        appliesTo = appliesTo,
        titlePattern = pattern,
        audioLanguages = audio,
        subtitleLanguages = subtitle,
        subtitleMode = mode,
    )

    private fun stream(
        type: StreamType,
        language: String? = null,
        displayTitle: String? = null,
        isForced: Boolean = false,
    ) = MediaStream(
        index = 0,
        type = type,
        language = language,
        displayTitle = displayTitle,
        isForced = isForced,
    )

    private fun badgeList() = listOf(com.raulshma.jellyplay.feature.player.video.engine.TrackBadge.FORCED)

    @Test
    fun `mediaRuleContentType maps media types`() {
        assertEquals(RuleContentType.MOVIE, mediaRuleContentType(MediaType.MOVIE))
        assertEquals(RuleContentType.EPISODE, mediaRuleContentType(MediaType.EPISODE))
        assertEquals(RuleContentType.ALL, mediaRuleContentType(MediaType.SERIES))
        assertEquals(RuleContentType.ALL, mediaRuleContentType(null))
    }
}
