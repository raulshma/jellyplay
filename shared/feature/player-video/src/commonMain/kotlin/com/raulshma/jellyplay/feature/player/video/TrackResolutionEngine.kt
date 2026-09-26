package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.LanguageRule
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.RuleContentType
import com.raulshma.jellyplay.core.model.SubtitleTrackMode
import com.raulshma.jellyplay.core.model.TrackSelectionPreset
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge
import java.util.concurrent.ConcurrentHashMap

/**
 * What the playing item is, for rule-matching purposes: the content type the
 * rules' `appliesTo` gate reads, the display names the `titlePattern` regexes
 * match against (series name first, then the item's own name — a rule may pin
 * either), and the server streams the signs/songs classification consults.
 */
@Immutable
data class TrackRuleContext(
    val contentType: RuleContentType,
    /** Match targets for a rule's title pattern — series name first, item name second. */
    val titles: List<String>,
    /** The server stream list; only consulted by [TrackResolutionEngine.isSignsTrack] users. */
    val streams: List<MediaStream> = emptyList(),
)

/**
 * The languages/modes the rule engine resolved for the current item. A `null`
 * language means "no opinion on that axis" — the existing restore ladder
 * falls through exactly as before. [subtitleDisabled] is the explicit-off
 * intent (rule mode OFF or a DUBBED preset); [subtitleMode] carries the
 * remaining mode so the caller can bias full-dialogue vs signs matching.
 */
@Immutable
data class TrackResolution(
    val audioLanguage: String?,
    val subtitleLanguage: String?,
    val subtitleMode: SubtitleTrackMode,
    val subtitleDisabled: Boolean,
)

/**
 * The pure resolution half of the subtitle/audio **language rule engine**:
 * given the persisted [LanguageRuleSet] and what is playing, decide
 * *what languages and subtitle mode to want*. It does not pick tracks — the
 * existing matchers ([SubtitleTrackMatcher], [TrackSelectionPolicy]) keep
 * doing that; this engine only feeds them the language/mode, so forced-only,
 * audio-description and remembered-track behaviour stay untouched.
 *
 * Resolution rung order:
 *
 *  1. **First matching rule** — declared order, gated by `appliesTo`
 *     ([TrackRuleContext.contentType]) and `titlePattern` (case-insensitive
 *     regex over [TrackRuleContext.titles]; null/blank pattern = any title).
 *  2. **Preset defaults** — the structural intent of the chosen
 *     [TrackSelectionPreset] (subbed vs dubbed, shows vs everything). See the
 *     preset KDoc; shows-scoped presets give movies no opinion.
 *  3. **Global rule-set languages** — the ordered `audioLanguages` /
 *     `subtitleLanguages` lists, when non-empty.
 *  4. `null` — no opinion; the existing ladder (per-series preference rung
 *     above this engine, global preferred language below it) applies unchanged.
 *
 * An inactive rule set (`LanguageRuleSet.isActive == false`: MANUAL with
 * nothing configured) short-circuits to `null`, pinning the byte-identical
 * passthrough contract. The allow-lists do NOT act here — they are candidate
 * filters applied by the track-selection ladder (`TrackSelectionHelper`),
 * independent of which rung produced a language.
 */
internal object TrackResolutionEngine {

    /**
     * Signs/songs track titles: standalone sign/song/opening/ending/lyric/
     * credit markers, singular and plural. Word-bounded so "ending" matches
     * but "legendary" and "editor" do not; case-insensitive via the embedded
     * flag. (The canonical Jellyfin track title is "Signs & Songs" — the
     * plurals are load-bearing.)
     */
    val SIGNS_TITLE_REGEX = Regex("""(?i)\b(signs?|songs? ?tracks?|op|ed|opening|ending|lyrics?|credits?)\b""")

    /** Compiled forms of the configured rule patterns, cached across restore runs. */
    private val ruleRegexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Signs/songs classification of a server stream: a title matching
     * [SIGNS_TITLE_REGEX], or an explicitly forced track (forced streams
     * carry only the foreign-dialogue/sign lines). Blank titles never match.
     */
    fun isSignsTrack(stream: MediaStream): Boolean {
        if (stream.isForced) return true
        val title = stream.displayTitle?.takeIf { it.isNotBlank() }
            ?: stream.title?.takeIf { it.isNotBlank() }
            ?: return false
        return SIGNS_TITLE_REGEX.containsMatchIn(title)
    }

    /**
     * Signs/songs classification of a picker row: the label is the enriched
     * server display title (see [TrackEnrichmentResolver]), so the same
     * regex applies; a FORCED badge is the forced-stream equivalent for
     * engines that never exposed a title.
     */
    fun isSignsTrack(track: TrackOption): Boolean {
        if (track.badges.contains(TrackBadge.FORCED)) return true
        if (track.label.isBlank()) return false
        return SIGNS_TITLE_REGEX.containsMatchIn(track.label)
    }

    /**
     * Runs the resolution rungs against [ruleSet]. Returns `null` when the
     * rule set is inactive or no rung produced an opinion for the context —
     * the caller then falls through the existing ladder unchanged.
     */
    fun resolve(ruleSet: LanguageRuleSet, context: TrackRuleContext): TrackResolution? {
        if (!ruleSet.isActive) return null

        // Rung 1 — first matching rule, in declared order.
        ruleSet.rules.firstOrNull { rule -> ruleMatches(rule, context) }?.let { rule ->
            return TrackResolution(
                audioLanguage = rule.audioLanguages.firstOrNull(),
                subtitleLanguage = rule.subtitleLanguages.firstOrNull(),
                subtitleMode = rule.subtitleMode,
                subtitleDisabled = rule.subtitleMode == SubtitleTrackMode.OFF,
            )
        }

        // Rung 2 — preset structural defaults. Shows-scoped presets give
        // movies no opinion (fall through to rung 3 / null).
        presetResolution(ruleSet, context.contentType)?.let { return it }

        // Rung 3 — global rule-set languages.
        if (ruleSet.audioLanguages.isNotEmpty() || ruleSet.subtitleLanguages.isNotEmpty()) {
            return TrackResolution(
                audioLanguage = ruleSet.audioLanguages.firstOrNull(),
                subtitleLanguage = ruleSet.subtitleLanguages.firstOrNull(),
                subtitleMode = SubtitleTrackMode.FULL,
                subtitleDisabled = false,
            )
        }

        // Rung 4 — no opinion.
        return null
    }

    /**
     * The structural default a preset implies for [contentType], or null when
     * the preset has no opinion (MANUAL, CUSTOM, or a shows-scoped preset
     * asked about a movie). Subbed presets want subtitles (language from the
     * rule-set subtitle list when configured); dubbed presets want the
     * rule-set audio language and subtitles off.
     */
    private fun presetResolution(ruleSet: LanguageRuleSet, contentType: RuleContentType): TrackResolution? {
        val preset = ruleSet.preset
        val applies = contentType == RuleContentType.EPISODE ||
            preset == TrackSelectionPreset.SUBBED_ALL ||
            preset == TrackSelectionPreset.DUBBED_ALL
        if (!applies) return null
        return when (preset) {
            TrackSelectionPreset.SUBBED_SHOWS,
            TrackSelectionPreset.SUBBED_ALL,
            -> TrackResolution(
                audioLanguage = null,
                subtitleLanguage = ruleSet.subtitleLanguages.firstOrNull(),
                subtitleMode = SubtitleTrackMode.FULL,
                subtitleDisabled = false,
            )
            TrackSelectionPreset.DUBBED_SHOWS,
            TrackSelectionPreset.DUBBED_ALL,
            -> TrackResolution(
                audioLanguage = ruleSet.audioLanguages.firstOrNull(),
                subtitleLanguage = null,
                subtitleMode = SubtitleTrackMode.OFF,
                subtitleDisabled = true,
            )
            else -> null
        }
    }

    /**
     * The rule gate: content type must match (ALL matches everything), and a
     * non-blank title pattern must match at least one of the context titles.
     * A corrupt pattern (invalid regex) never matches rather than throwing
     * into the restore hot path.
     */
    private fun ruleMatches(rule: LanguageRule, context: TrackRuleContext): Boolean {
        if (rule.appliesTo != RuleContentType.ALL && rule.appliesTo != context.contentType) return false
        val pattern = rule.titlePattern?.takeIf { it.isNotBlank() } ?: return true
        // ConcurrentHashMap<..., Regex?> cannot store a null value — use an
        // empty never-matching regex as the "invalid" sentinel.
        val regex = ruleRegexCache.getOrPut(pattern) {
            try { Regex(pattern, RegexOption.IGNORE_CASE) } catch (_: Exception) { Regex("(?!x)x") }
        }
        return context.titles.any { title -> title.isNotBlank() && regex.containsMatchIn(title) }
    }

    /** Test hook: drops the compiled rule-pattern cache. */
    internal fun clearRegexCacheForTest() = ruleRegexCache.clear()
}
