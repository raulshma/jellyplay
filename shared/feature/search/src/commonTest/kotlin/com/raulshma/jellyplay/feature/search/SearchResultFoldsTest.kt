package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the search screen's pure result-presentation folds — the did-you-mean
 * typo heuristic ([didYouMeanSuggestions], extracted verbatim from the
 * NoResults branch) and the on-device section's offline-vs-online dedup
 * ([dedupeOfflineAgainstLibrary]). Pure JVM over the folds' inputs, the
 * [SearchSurfaceTest] pattern: every rule the screen used to decide inline
 * is asserted here.
 */
class SearchResultFoldsTest {

    private fun history(vararg queries: String) = queries.mapIndexed { i, q ->
        SearchHistoryItem(id = i.toLong(), query = q, searchedAt = i.toLong())
    }

    private fun offline(id: String) = OfflineMediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    private fun online(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    // ── didYouMeanSuggestions ────────────────────────────────────────────────

    @Test
    fun `sub-3-character queries suggest nothing`() {
        assertTrue(didYouMeanSuggestions("ab", history("abc")).isEmpty())
    }

    @Test
    fun `empty history suggests nothing`() {
        assertTrue(didYouMeanSuggestions("interstelar", emptyList()).isEmpty())
    }

    @Test
    fun `a shared leading run of three or more chars suggests`() {
        // Single-word typo: "Interstelar" vs history "Interstellar".
        assertEquals(
            listOf("Interstellar"),
            didYouMeanSuggestions("Interstelar", history("Interstellar", "Dune")),
        )
        // The prefix match is case-insensitive.
        assertEquals(
            listOf("interstellar"),
            didYouMeanSuggestions("INTE", history("interstellar")),
        )
    }

    @Test
    fun `a history token contained in the query suggests even without a prefix`() {
        // "star wars" shares no 3-char prefix with "the best star" but the
        // token "star" appears inside the query.
        assertEquals(
            listOf("star wars"),
            didYouMeanSuggestions("the best star", history("star wars")),
        )
    }

    @Test
    fun `short tokens do not suggest`() {
        // Two-char tokens are below the heuristic's floor.
        assertTrue(didYouMeanSuggestions("up in the air", history("up")).isEmpty())
    }

    @Test
    fun `the query itself never suggests itself`() {
        assertTrue(didYouMeanSuggestions("dune", history("dune")).isEmpty())
    }

    @Test
    fun `suggestions are distinct and capped at four`() {
        val dupes = history("dune two", "dune two", "dune ii", "dune iii", "dune iv", "dune v")
        val suggestions = didYouMeanSuggestions("dune", dupes)
        assertEquals(4, suggestions.size)
        assertEquals(suggestions.distinct(), suggestions)
    }

    // ── dedupeOfflineAgainstLibrary ──────────────────────────────────────────

    @Test
    fun `empty offline short-circuits unchanged`() {
        val library = listOf(online("a"))
        assertEquals(emptyList(), dedupeOfflineAgainstLibrary(emptyList(), library))
    }

    @Test
    fun `empty online set keeps every offline item`() {
        val downloads = listOf(offline("a"), offline("b"))
        assertEquals(downloads, dedupeOfflineAgainstLibrary(downloads, emptyList()))
    }

    @Test
    fun `items already in the library are dropped`() {
        val downloads = listOf(offline("a"), offline("b"), offline("c"))
        val library = listOf<MediaItem?>(online("a"), null, online("c"))
        assertEquals(listOf(offline("b")), dedupeOfflineAgainstLibrary(downloads, library))
    }

    @Test
    fun `blank library ids are ignored`() {
        val downloads = listOf(offline("a"))
        val library = listOf(online("  "))
        assertEquals(downloads, dedupeOfflineAgainstLibrary(downloads, library))
    }
}
