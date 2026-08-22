package com.raulshma.jellyplay.core.ui.settingssearch

/**
 * Fuzzy, ranked matcher for the settings search registry.
 *
 * The registry contains jargon-heavy titles and (sometimes thin) keyword lists, so the previous
 * strict `contains` search left many advanced settings effectively unfindable — typos
 * ("passthru", "framrate"), merged/split terms ("framerate" vs "frame rate"), and synonyms all
 * returned nothing. This matcher scores each [SettingsSearchItem] against the query and returns
 * results sorted by relevance, dropping anything below [MIN_SCORE].
 *
 * Matching is pure and dependency-free so it can be unit-tested on the JVM without Compose/Android;
 * callers resolve [SettingsSearchRegistry.items] to [ResolvedSettingsItem] (locale-aware text)
 * before invoking [search].
 */
object SettingsSearchMatcher {

    /** A query must clear this to appear in results. */
    private const val MIN_SCORE = 0.45

    /** Per-token scoring bands. */
    private const val EXACT = 1.0
    private const val PREFIX = 0.9
    private const val SUBSTRING = 0.7
    private const val WORD_PREFIX = 0.6
    private const val FUZZY_FLOOR = 0.6

    /**
     * Score [item] against [query]. Higher is better; 0.0 means no usable match.
     *
     * The query is split into whitespace tokens with AND semantics: every token must match at
     * least one candidate field, otherwise the whole item scores 0. This keeps multi-word queries
     * precise (e.g. "audio delay" must match both "audio" and "delay"). The final score is the
     * average of the best per-token scores, boosted by which field matched.
     *
     * Matching runs against the locale-resolved [ResolvedSettingsItem] text, so queries typed in
     * the user's language hit the translated titles/subtitles/categories.
     */
    fun scoreItem(query: String, item: ResolvedSettingsItem): Double {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return 0.0

        // (candidate string, field weight) — category weighted low so it doesn't dominate.
        val candidates: List<Pair<String, Double>> = buildList {
            add(item.title to 1.5)
            add(item.subtitle to 1.0)
            item.item.keywords.forEach { add(it to 1.1) }
            add(item.category to 0.6)
        }

        var tokenSum = 0.0
        for (token in tokens) {
            val bestTokenScore = candidates.maxOf { (candidate, weight) ->
                matchScore(token, candidate) * weight
            }
            if (bestTokenScore <= 0.0) return 0.0 // AND: every token must contribute
            tokenSum += bestTokenScore
        }
        return tokenSum / tokens.size
    }

    /**
     * Return all [items] matching [query], sorted best-first. Ties preserve registry order via a
     * stable sort, so the curated ordering still acts as a tiebreaker.
     */
    fun search(query: String, items: List<ResolvedSettingsItem>): List<ResolvedSettingsItem> {
        if (query.isBlank()) return emptyList()
        return items.asSequence()
            .map { it to scoreItem(query, it) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }

    private fun tokenize(query: String): List<String> =
        query.lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }

    /** Lowercase [s] once for comparison. */
    private fun String.norm() = lowercase()

    /** Best-effort similarity in [0.0, 1.0] between a query [token] and a [candidate] string. */
    private fun matchScore(token: String, candidateRaw: String): Double {
        if (token.isEmpty()) return 0.0
        val candidate = candidateRaw.norm()
        if (candidate.isEmpty()) return 0.0

        if (token == candidate) return EXACT
        if (candidate.startsWith(token)) return PREFIX
        if (candidate.contains(token)) return SUBSTRING

        // Word-prefix within the candidate, e.g. token "frame" inside "frame rate match".
        val words = candidate.split(' ', '-', '_', '/')
        if (words.any { it.startsWith(token) || it == token }) return WORD_PREFIX

        // Damerau-Levenshtein fuzzy for typos, only if reasonably close.
        val distance = damerauLevenshtein(token, candidate)
        val maxLen = maxOf(token.length, candidate.length)
        if (maxLen == 0) return 0.0
        val fuzzy = 1.0 - distance.toDouble() / maxLen
        return if (fuzzy >= FUZZY_FLOOR) fuzzy else 0.0
    }

    /**
     * Damerau-Levenshtein edit distance (insertions, deletions, substitutions, adjacent
     * transpositions). Classic two-row dynamic programming implementation.
     */
    private fun damerauLevenshtein(a: String, b: String): Int {
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la

        // Two rolling rows; we also need the previous-previous row for transposition checks.
        var prevPrev = IntArray(lb + 1) { it }
        var prev = IntArray(lb + 1)
        var curr = IntArray(lb + 1)
        prev[0] = 1
        for (j in 1..lb) prev[j] = j

        for (i in 1..la) {
            curr[0] = i
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,        // deletion
                    curr[j - 1] + 1,    // insertion
                    prev[j - 1] + cost  // substitution
                )
                // Adjacent transposition.
                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
                ) {
                    curr[j] = minOf(curr[j], prevPrev[j - 2] + cost)
                }
            }
            val tmp = prevPrev; prevPrev = prev; prev = curr; curr = tmp
        }
        return prev[lb]
    }
}
