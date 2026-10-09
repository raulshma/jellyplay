package com.raulshma.jellyplay.core.model.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the [ContinueWatchingRowRule] — the Continue Watching row's single
 * owner (the GLOSSARY home-row-module seed) — whose consumers are the online
 * fetch path, the single-row refresh, the ordering use case, and the offline
 * home mirror:
 *
 *  - the #157 played-row exclusion, in both halves ([filterResumable] drops
 *    played rows and books; [filterReadingResumable] keeps only unplayed
 *    books — the two partition every row without overlap);
 *  - the hidden-item drop (`hiddenCwItemIds`);
 *  - Next Up eligibility (CW-overlap drop + the "remove from Next Up" series
 *    blocklist, null-series items always eligible);
 *  - the CW + Next Up merge: CW first, then the Next Up items whose id is
 *    seen for the first time;
 *  - the generic accessor members the offline mirror drives with its local
 *    playstate (`isPlayed` = played OR finished-offline) and item type.
 */
class ContinueWatchingRowRuleTest {

    private fun item(
        id: String,
        played: Boolean = false,
        mediaType: MediaType = MediaType.MOVIE,
        seriesId: String? = null,
        position: Long? = 30_000_000L,
    ) = MediaItem(
        id = id,
        name = id,
        mediaType = mediaType,
        isPlayed = played,
        playbackPositionTicks = position,
        seriesId = seriesId,
    )

    // ── #157 played-row exclusion — the video half ──────────────────────────

    @Test
    fun `filterResumable drops played rows even with a stale resume position`() {
        val rows = listOf(item("resumable"), item("poisoned", played = true))

        assertEquals(listOf("resumable"), ContinueWatchingRowRule.filterResumable(rows).map { it.id })
    }

    @Test
    fun `filterResumable drops books outright - they ride their own row`() {
        val rows = listOf(item("movie"), item("book", mediaType = MediaType.BOOK))

        assertEquals(listOf("movie"), ContinueWatchingRowRule.filterResumable(rows).map { it.id })
    }

    @Test
    fun `an all-played row collapses to empty and empty stays empty`() {
        assertEquals(emptyList(), ContinueWatchingRowRule.filterResumable(listOf(item("a", played = true))))
        assertEquals(emptyList(), ContinueWatchingRowRule.filterResumable(emptyList()))
    }

    // ── #157 played-row exclusion — the books half ──────────────────────────

    @Test
    fun `filterReadingResumable keeps only unplayed books`() {
        val rows = listOf(
            item("book-open", mediaType = MediaType.BOOK),
            item("book-done", played = true, mediaType = MediaType.BOOK),
            item("movie"),
        )

        assertEquals(listOf("book-open"), ContinueWatchingRowRule.filterReadingResumable(rows).map { it.id })
    }

    @Test
    fun `the two halves partition every row without overlap`() {
        val rows = listOf(
            item("m"),
            item("b", mediaType = MediaType.BOOK),
            item("mp", played = true),
            item("bp", played = true, mediaType = MediaType.BOOK),
        )

        assertEquals(listOf("m"), ContinueWatchingRowRule.filterResumable(rows).map { it.id })
        assertEquals(listOf("b"), ContinueWatchingRowRule.filterReadingResumable(rows).map { it.id })
    }

    // ── The hidden-item drop ────────────────────────────────────────────────

    @Test
    fun `excludingHiddenItems drops only the hidden ids`() {
        val rows = listOf(item("a"), item("hidden"), item("b"))

        assertEquals(
            listOf("a", "b"),
            ContinueWatchingRowRule.excludingHiddenItems(rows, setOf("hidden")).map { it.id },
        )
        assertEquals(
            listOf("a", "hidden", "b"),
            ContinueWatchingRowRule.excludingHiddenItems(rows, emptySet()).map { it.id },
        )
    }

    // ── The Continue Watching id capture ────────────────────────────────────

    @Test
    fun `continueWatchingFilterIds is the id set of the HIDDEN-FILTERED list`() {
        val rows = listOf(item("a"), item("hidden"), item("b"))

        assertEquals(
            setOf("a", "b"),
            ContinueWatchingRowRule.continueWatchingFilterIds(rows, hiddenItemIds = setOf("hidden")),
        )
        // A hidden item stays out of the set, so it can never block a Next Up
        // entry — the "hide from CW, keep eligible for Next Up" affordance.
        assertEquals(
            setOf("a", "hidden"),
            ContinueWatchingRowRule.continueWatchingFilterIds(rows, hiddenItemIds = setOf("b")),
        )
    }

    // ── Next Up eligibility ─────────────────────────────────────────────────

    @Test
    fun `nextUpEligible drops CW overlap and blocklisted series`() {
        val nextUp = listOf(
            item("inCw"),
            item("excluded", seriesId = "s1"),
            item("kept"),
        )

        assertEquals(
            listOf("kept"),
            ContinueWatchingRowRule.nextUpEligible(
                nextUp,
                continueWatchingIds = setOf("inCw"),
                excludedSeriesIds = setOf("s1"),
            ).map { it.id },
        )
    }

    @Test
    fun `a seriesless next up item is never blocklisted`() {
        val nextUp = listOf(item("standalone", seriesId = null), item("blocked", seriesId = "s1"))

        assertEquals(
            listOf("standalone"),
            ContinueWatchingRowRule.nextUpEligible(
                nextUp,
                continueWatchingIds = emptySet(),
                excludedSeriesIds = setOf("s1"),
            ).map { it.id },
        )
    }

    // ── The CW + Next Up merge ──────────────────────────────────────────────

    @Test
    fun `mergeCwNextUp emits CW first then the unseen Next Up tail in order`() {
        val cw = listOf(item("cw1"), item("shared"))
        val nextUp = listOf(item("shared"), item("next1"), item("next2"))

        assertEquals(
            listOf("cw1", "shared", "next1", "next2"),
            ContinueWatchingRowRule.mergeCwNextUp(cw, nextUp).map { it.id },
        )
    }

    @Test
    fun `mergeCwNextUp collapses duplicates within the Next Up tail too`() {
        val cw = listOf(item("cw1"))
        val nextUp = listOf(item("next1"), item("next1"), item("next2"))

        assertEquals(
            listOf("cw1", "next1", "next2"),
            ContinueWatchingRowRule.mergeCwNextUp(cw, nextUp).map { it.id },
        )
    }

    @Test
    fun `mergeCwNextUp passes the CW list through verbatim`() {
        val cw = listOf(item("cw1"), item("cw2"))
        val nextUp = listOf(item("cw2"))

        assertEquals(
            listOf("cw1", "cw2"),
            ContinueWatchingRowRule.mergeCwNextUp(cw, nextUp).map { it.id },
        )
        assertEquals(
            listOf("cw1", "cw2"),
            ContinueWatchingRowRule.mergeCwNextUp(cw, emptyList()).map { it.id },
        )
    }

    // ── The divergent parameter paths (the offline mirror's projection) ─────

    /**
     * A stand-in for `OfflineMediaItem`: the offline mirror's local "played"
     * notion is played OR finished-offline, and the offline item type is not
     * [MediaItem] — the rule's generic members take the projection as
     * parameters instead of forcing a conversion.
     */
    private data class LocalRow(val id: String, val localPlayed: Boolean, val mediaType: MediaType)

    private val LocalRowIsPlayed: (LocalRow) -> Boolean = { it.localPlayed }

    @Test
    fun `generic filterResumable honors the caller's played notion`() {
        val rows = listOf(
            LocalRow("open", localPlayed = false, mediaType = MediaType.EPISODE),
            // Finished-offline locally but the played flag never landed — the
            // offline isWatchedOffline projection must drop it like a played row.
            LocalRow("finished-offline", localPlayed = true, mediaType = MediaType.EPISODE),
            LocalRow("book", localPlayed = false, mediaType = MediaType.BOOK),
        )

        assertEquals(
            listOf("open"),
            ContinueWatchingRowRule.filterResumable(rows, isPlayed = LocalRowIsPlayed, mediaType = { it.mediaType })
                .map { it.id },
        )
        assertEquals(
            listOf("book"),
            ContinueWatchingRowRule.filterReadingResumable(rows, isPlayed = LocalRowIsPlayed, mediaType = { it.mediaType })
                .map { it.id },
        )
    }

    @Test
    fun `generic members drive the offline projection end to end`() {
        val cw = listOf(LocalRow("cw1", localPlayed = false, mediaType = MediaType.EPISODE))
        val nextUp = listOf(
            LocalRow("cw1", localPlayed = false, mediaType = MediaType.EPISODE),
            LocalRow("n1", localPlayed = false, mediaType = MediaType.EPISODE),
        )
        val id: (LocalRow) -> String = { it.id }

        assertEquals(
            listOf("cw1", "n1"),
            ContinueWatchingRowRule.mergeCwNextUp(cw, nextUp, id).map { it.id },
        )
        assertEquals(
            listOf("cw1", "n1"),
            ContinueWatchingRowRule.excludingHiddenItems(
                listOf(
                    LocalRow("cw1", localPlayed = false, mediaType = MediaType.EPISODE),
                    LocalRow("hidden", localPlayed = false, mediaType = MediaType.EPISODE),
                    LocalRow("n1", localPlayed = false, mediaType = MediaType.EPISODE),
                ),
                setOf("hidden"),
                id,
            ).map { it.id },
        )
        assertEquals(
            setOf("cw1"),
            ContinueWatchingRowRule.continueWatchingFilterIds(cw, emptySet(), id),
        )
    }

    @Test
    fun `generic nextUpEligible keys the blocklist on the projected series id`() {
        val nextUp = listOf(
            LocalRow("blocked", localPlayed = false, mediaType = MediaType.EPISODE),
            LocalRow("kept", localPlayed = false, mediaType = MediaType.EPISODE),
        )
        // LocalRow projects a constant series id — "blocked" rides series s1,
        // "kept" projects null (seriesless) and survives any blocklist.
        val seriesId: (LocalRow) -> String? = { if (it.id == "blocked") "s1" else null }

        assertEquals(
            listOf("kept"),
            ContinueWatchingRowRule.nextUpEligible(
                nextUp,
                continueWatchingIds = emptySet(),
                excludedSeriesIds = setOf("s1"),
                id = { it.id },
                seriesId = seriesId,
            ).map { it.id },
        )
    }
}
