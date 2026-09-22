package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The track-selection preset — the headline knob of the language rule engine.
 * Encodes the structural intent that applies when no explicit
 * [LanguageRule] matches:
 *
 *  - [MANUAL]: the engine is inert — restore behaviour is byte-identical to
 *    the pre-engine ladder (the default, and the escape hatch).
 *  - [SUBBED_SHOWS]: episodes play subbed (subtitles wanted, original audio);
 *    movies get no preset opinion.
 *  - [DUBBED_SHOWS]: episodes play dubbed (audio from the rule-set list,
 *    subtitles off); movies get no preset opinion.
 *  - [SUBBED_ALL]: every content type plays subbed.
 *  - [DUBBED_ALL]: every content type plays dubbed (subtitles off).
 *  - [CUSTOM]: no structural default of its own — only the configured rules,
 *    rule-set languages and allow-lists apply.
 */
@Immutable
@Serializable
enum class TrackSelectionPreset(override val displayName: String) : HasDisplayName {
    MANUAL("Manual (per-series overrides only)"),
    SUBBED_SHOWS("Subbed shows"),
    DUBBED_SHOWS("Dubbed shows"),
    SUBBED_ALL("Subbed (everything)"),
    DUBBED_ALL("Dubbed (everything)"),
    CUSTOM("Custom rules"),
}

/**
 * The content type a [LanguageRule] applies to. Drives the rule gate and the
 * preset scope in [TrackSelectionPreset]; ALL matches movies and episodes
 * alike.
 */
@Immutable
@Serializable
enum class RuleContentType(override val displayName: String) : HasDisplayName {
    ALL("All content"),
    MOVIE("Movies"),
    EPISODE("Episodes"),
}

/**
 * Maps a Jellyfin [MediaType] onto the rule gate: episodes gate EPISODE rules,
 * movies gate MOVIE rules, everything else matches only ALL rules.
 */
fun mediaRuleContentType(type: MediaType?): RuleContentType = when (type) {
    MediaType.MOVIE -> RuleContentType.MOVIE
    MediaType.EPISODE -> RuleContentType.EPISODE
    else -> RuleContentType.ALL
}

/**
 * What kind of subtitle track the engine should want for a matching item.
 * FULL and FULL_PREFER_SIGNS both enable subtitles; they differ only in which
 * same-language track wins on a tie (full dialogue vs the signs/songs track).
 * OFF maps to the explicit "subtitles off" intent, FORCED_ONLY to the
 * existing forced-subtitles path.
 */
@Immutable
@Serializable
enum class SubtitleTrackMode(override val displayName: String) : HasDisplayName {
    OFF("Off"),
    FORCED_ONLY("Forced only"),
    FULL("Full dialogue"),
    FULL_PREFER_SIGNS("Signs / songs when present"),
}

/**
 * One declarative language rule: "for [appliesTo] items whose series/name
 * matches [titlePattern], want these audio/subtitle languages in this
 * subtitle [subtitleMode]".
 *
 * A rule with a null/blank [titlePattern] matches by content type alone.
 * [audioLanguages]/[subtitleLanguages] are ORDERED preferences — the first
 * entry the item actually has a track for wins (the matcher relaxes through
 * the list via the ordinary language ladder).
 */
@Immutable
@Serializable
data class LanguageRule(
    val id: String,
    val appliesTo: RuleContentType = RuleContentType.ALL,
    /** Regex, case-insensitive, matched against the series/item display names. Null = any title. */
    val titlePattern: String? = null,
    val audioLanguages: List<String> = emptyList(),
    val subtitleLanguages: List<String> = emptyList(),
    val subtitleMode: SubtitleTrackMode = SubtitleTrackMode.FULL,
)

/**
 * The persisted track-language rule set (stored as JSON under the
 * `track_selection_rules` preference key). Pure data — the resolution itself
 * lives in the player-video module's `TrackResolutionEngine`, so this model
 * stays free of player dependencies and settings can read it too.
 *
 * Resolution rung order (engine-side): first matching [rules] entry
 * (declared order, gated by appliesTo/titlePattern) → preset defaults → the
 * global [audioLanguages]/[subtitleLanguages] lists → no opinion (the
 * existing restore ladder falls through unchanged). The two allow-lists are
 * independent of the rungs: whenever non-empty they exclude out-of-list
 * tracks from *auto* selection entirely (mpv `lang_filter` semantics;
 * manual/held selections are unaffected).
 *
 * `preset == MANUAL` with everything else empty is the identity rule set —
 * the engine reports "no opinion" and behaviour is byte-identical to the
 * pre-engine ladder.
 */
@Immutable
@Serializable
data class LanguageRuleSet(
    val preset: TrackSelectionPreset = TrackSelectionPreset.MANUAL,
    /** Global ordered audio preference, applied when no rule/preset opinion exists. */
    val audioLanguages: List<String> = emptyList(),
    /** Global ordered subtitle preference, applied when no rule/preset opinion exists. */
    val subtitleLanguages: List<String> = emptyList(),
    /** Auto-selected audio tracks outside this set are never chosen. Empty = no filter. */
    val audioAllowList: Set<String> = emptySet(),
    /** Auto-selected subtitle tracks outside this set are never chosen. Empty = no filter. */
    val subtitleAllowList: Set<String> = emptySet(),
    val rules: List<LanguageRule> = emptyList(),
) {
    /**
     * False when the rule set cannot influence resolution at all. MANUAL is
     * the explicit "no automation" choice — byte-identical passthrough, always
     * inert (configured rules/languages/allow-lists are retained in storage
     * but not applied until the user picks another preset). CUSTOM needs
     * actual content to do anything. The four structural presets
     * (subbed/dubbed × shows/all) are meaningful on their own — "dubbed,
     * subtitles off" requires no language list — so they are always active.
     */
    val isActive: Boolean
        get() = when (preset) {
            TrackSelectionPreset.MANUAL -> false
            TrackSelectionPreset.CUSTOM ->
                rules.isNotEmpty() ||
                    audioLanguages.isNotEmpty() ||
                    subtitleLanguages.isNotEmpty() ||
                    audioAllowList.isNotEmpty() ||
                    subtitleAllowList.isNotEmpty()
            else -> true
        }

    /** True when at least one axis can narrow the candidates before matching. */
    val hasAllowList: Boolean
        get() = audioAllowList.isNotEmpty() || subtitleAllowList.isNotEmpty()
}
