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
 * before invoking [search]. The search pipeline resolves ONCE per subscription and matches many
 * times: it runs the catalog through [prepare] (text lowercased once, candidate lists assembled
 * once) and matches every later query against the prepared items — the public overloads here
 * wrap that same path one item / one query at a time.
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

    /** Per-field weights — category weighted low so it doesn't dominate. */
    private const val TITLE_WEIGHT = 1.5
    private const val SUBTITLE_WEIGHT = 1.0
    private const val KEYWORD_WEIGHT = 1.1
    private const val CATEGORY_WEIGHT = 0.6

    /**
     * The resolve-time normalization of one [ResolvedSettingsItem]: every matchable text field
     * lowercased ONCE and the weighted candidate list assembled ONCE, so a keystroke neither
     * re-lowercases ~4 fields × the whole catalog nor rebuilds the candidate list per item
     * (the former per-query cost). Produced by [prepare]; consumed by the internal
     * [search]/[score] overloads.
     */
    internal class PreparedItem(item: ResolvedSettingsItem) {
        val resolved: ResolvedSettingsItem = item

        /** (normalized candidate text, field weight) pairs, fixed for the item's lifetime. */
        val candidates: List<Pair<String, Double>> = buildList {
            add(item.title.norm() to TITLE_WEIGHT)
            add(item.subtitle.norm() to SUBTITLE_WEIGHT)
            item.item.keywords.forEach { add(it.norm() to KEYWORD_WEIGHT) }
            add(item.category.norm() to CATEGORY_WEIGHT)
        }
    }

    /** Pre-normalize a resolved catalog for repeated matching (the pipeline's resolve-once step). */
    internal fun prepare(items: List<ResolvedSettingsItem>): List<PreparedItem> =
        items.map(::PreparedItem)

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
    fun scoreItem(query: String, item: ResolvedSettingsItem): Double =
        score(PreparedItem(item), tokenize(query))

    /**
     * Return all [items] matching [query], sorted best-first. Ties preserve registry order via a
     * stable sort, so the curated ordering still acts as a tiebreaker.
     */
    fun search(query: String, items: List<ResolvedSettingsItem>): List<ResolvedSettingsItem> =
        search(query, prepare(items))

    /** The pre-normalized overload the resolve-once pipeline rides — one [tokenize] per query. */
    internal fun search(query: String, items: List<PreparedItem>): List<ResolvedSettingsItem> {
        if (query.isBlank()) return emptyList()
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return items.asSequence()
            .map { it to score(it, tokens) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second }
            .map { it.first.resolved }
            .toList()
    }

    private fun score(item: PreparedItem, tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0
        var tokenSum = 0.0
        for (token in tokens) {
            val bestTokenScore = item.candidates.maxOf { (candidate, weight) ->
                matchScore(token, candidate) * weight
            }
            if (bestTokenScore <= 0.0) return 0.0 // AND: every token must contribute
            tokenSum += bestTokenScore
        }
        return tokenSum / tokens.size
    }

    private fun tokenize(query: String): List<String> =
        query.lowercase().split(' ', '\t', '\n').filter { it.isNotBlank() }

    /** Lowercase [s] once for comparison. */
    private fun String.norm() = lowercase()

    /**
     * Best-effort similarity in [0.0, 1.0] between a query [token] and a [candidate] string.
     * The candidate arrives pre-normalized ([PreparedItem] lowercased it once at prepare time),
     * so no per-token lowercase happens here.
     */
    private fun matchScore(token: String, candidate: String): Double {
        if (token.isEmpty()) return 0.0
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
