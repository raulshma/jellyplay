package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.isLanguageMatch
import com.raulshma.jellyplay.feature.player.video.engine.TrackBadge

/**
 * Pure, engine-less subtitle-track selection for the "remember subtitle for
 * series" preference. Extracted from `TrackSelectionHelper` so the tiered
 * relaxation logic is unit-testable without the heavy engine/ViewModel wiring.
 *
 * Given the available [TrackOption]s and a remembered descriptor (language +
 * optional forced / SDH role), [match] returns the best track via tiered
 * relaxation so the user always lands on the closest-available subtitle and
 * never silently on "Off" unless no same-language track exists at all.
 *
 * A `null` role field means "don't care about that role", so a legacy
 * preference row (both fields null, pre the v38→v39 role migration) collapses
 * every tier to "any same-language" — zero behaviour change for existing users.
 */
internal object SubtitleTrackMatcher {

    /**
     * Tiered resolution against [tracks]:
     *
     *  1. **Exact** — language + forced-role + SDH-role all match intent.
     *  2. **Relax SDH** — language + forced-role match (SDH dropped).
     *  3. **Language only** — any same-language track (today's pre-role behaviour).
     *
     * Returns null when no same-language track exists; the caller then falls
     * back to "Off". When a tier has several candidates, the DEFAULT-badged track
     * wins, then the lowest index (deterministic).
     *
     * @param lang ISO-639 language code the preference resolved to.
     * @param forced whether the preference pins a forced-narrative track, or null.
     * @param hearingImpaired whether the preference pins an SDH track, or null.
     */
    fun match(
        tracks: List<TrackOption>,
        lang: String,
        forced: Boolean?,
        hearingImpaired: Boolean?,
    ): TrackOption? {
        val langMatches = tracks.filter { it.index >= 0 && isLanguageMatch(it.language, lang) }
        if (langMatches.isEmpty()) return null
        // Tier 1 — exact descriptor (language + forced-role + sdh-role).
        langMatches.filter { roleMatches(it, forced, hearingImpaired) }
            .bestTiebreak()?.let { return it }
        // Tier 2 — relax SDH, keep forced-role.
        langMatches.filter { roleMatches(it, forced, sdh = null) }
            .bestTiebreak()?.let { return it }
        // Tier 3 — language only.
        return langMatches.bestTiebreak()
    }

    /**
     * True when the track's role badges satisfy the intent. A `null` intent axis
     * is satisfied by any track (we don't care); a non-null axis requires the
     * badge presence/absence to match it exactly.
     */
    private fun roleMatches(track: TrackOption, forced: Boolean?, sdh: Boolean?): Boolean {
        val forcedOk = forced == null || track.badges.contains(TrackBadge.FORCED) == forced
        val sdhOk = sdh == null || track.badges.contains(TrackBadge.SDH) == sdh
        return forcedOk && sdhOk
    }

    /**
     * Deterministic tie-break across several equally-good candidates: prefer the
     * DEFAULT-badged track (often the canonical language track), then the lowest
     * engine index (stable track order). Returns null for an empty list.
     */
    private fun List<TrackOption>.bestTiebreak(): TrackOption? =
        minWithOrNull(compareBy({ !it.badges.contains(TrackBadge.DEFAULT) }, { it.index }))
}
