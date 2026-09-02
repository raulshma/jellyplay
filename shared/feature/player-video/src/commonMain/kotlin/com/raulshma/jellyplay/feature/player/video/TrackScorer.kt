package com.raulshma.jellyplay.feature.player.video

/**
 * Cross-episode track **scoring** — ranks tracks against the last selected.
 *
 * JellyPlay already resolves tracks by container stream index (exact) → same-language
 * positional → title match (see `TrackEnrichmentResolver`). That resolution,
 * however, only fires when the *same item* is re-loaded or when a per-series
 * **language** preference applies. When auto-playing the *next episode* of a
 * series, a user who picked e.g. a specific "English · 5.1 · DTS" track would,
 * without scoring, fall back to any English track — losing the channel/codec
 * preference.
 *
 * This scorer ranks the next episode's candidate tracks against the *last
 * selected* track of the same type, summing weighted signals:
 *  - language match: +2 (the dominant signal — most users pick by language)
 *  - title/label match: +2 (channel layout, "Commentary", "SDH", and codec
 *    all live here — [TrackOption] folds codec into the label, so a separate
 *    codec signal would double-count what the label branch already credits)
 *  - same relative index within the language group: +1 (positional stability
 *    across episodes of a remux with identical track layouts)
 *
 * A candidate must reach [minScore] (default 3, matching web's threshold) to be
 * chosen; otherwise `null` is returned and the caller falls back to its existing
 * language-rule resolution. This keeps scoring strictly additive: the language
 * fallback remains the safety net when the next episode's tracks differ enough
 * that no confident match exists.
 *
 * Pure and side-effect-free so it is fully unit-testable without an engine.
 */
object TrackScorer {

    /**
     * @param lastLanguage   language code of the previously selected track (ISO-639-2/3), nullable.
     * @param lastLabel      display label of the previously selected track (codec folded in).
     * @param candidates     the new item's tracks as (language, label, indexWithinLanguage) tuples.
     * @param minScore       minimum score to accept a match. Defaults to 3 (web parity).
     * @return the best-scoring candidate, or null if none meets [minScore].
     */
    fun bestMatch(
        lastLanguage: String?,
        lastLabel: String,
        candidates: List<Candidate>,
        minScore: Int = 3,
    ): Candidate? {
        if (candidates.isEmpty()) return null
        val normLastLang = lastLanguage?.lowercase()?.trim().orEmpty()
        val normLastLabel = lastLabel.lowercase().trim()

        val scored = candidates.mapNotNull { c ->
            val score = score(normLastLang, normLastLabel, c)
            if (score >= minScore) c to score else null
        }
        // Highest score wins; ties break by lower positional index (stable layout).
        return scored.maxByOrNull { it.second }?.first
    }

    private fun score(
        normLastLang: String,
        normLastLabel: String,
        candidate: Candidate,
    ): Int {
        var score = 0
        val normCandLang = candidate.language.lowercase().trim()
        val normCandLabel = candidate.label.lowercase().trim()

        if (normLastLang.isNotEmpty() && normCandLang == normLastLang) score += 2
        // Title/label equality is a strong signal; a substring overlap (e.g. "5.1",
        // "Commentary", "SDH", a codec like "DTS") is informative too. To avoid a
        // generic "English" track stealing the match from a specific "English ·
        // Commentary" one, substring credit is only given when the candidate is
        // the more specific (longer) of the two — i.e. it adds detail the
        // previously selected track also carried, rather than being a bare prefix.
        if (normLastLabel.isNotEmpty() && normCandLabel.isNotEmpty()) {
            score += when {
                normCandLabel == normLastLabel -> 2
                normCandLabel.length > normLastLabel.length && normCandLabel.contains(normLastLabel) -> 2
                normLastLabel.length > normCandLabel.length && normLastLabel.contains(normCandLabel) -> 1
                else -> 0
            }
        }
        // Same positional order within the language group is a layout-stability signal.
        if (candidate.indexWithinLanguage >= 0 && candidate.indexWithinLanguage == candidate.candidateIndexWithinLanguage) score += 1
        return score
    }

    /**
     * A candidate track projection for scoring. [indexWithinLanguage] is the
     * *previously selected* track's position within its language group;
     * [candidateIndexWithinLanguage] is *this* candidate's position within the
     * new item's same-language group. -1 means "unknown / not comparable".
     */
    data class Candidate(
        val language: String,
        val label: String,
        val indexWithinLanguage: Int = -1,
        val candidateIndexWithinLanguage: Int = -1,
        /** Opaque id the caller uses to map the winning candidate back to its TrackOption. */
        val optionId: Int = -1,
    )
}
