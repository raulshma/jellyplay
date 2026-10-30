package com.raulshma.jellyplay.feature.downloads

import androidx.compose.ui.state.ToggleableState
import com.raulshma.jellyplay.core.data.repository.DownloadProgress
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-function coverage for the downloads-list grouping (gh-175): numeric
 * season/episode ordering independent of download order, series/season group
 * shape, legacy-row fallbacks, the filter/sort partition, and the tri-state
 * group-selection algebra.
 */
class DownloadLibraryGroupingTest {

    private fun episode(
        id: String,
        seriesId: String? = "s1",
        seriesName: String? = "Show",
        seasonId: String? = null,
        seasonName: String? = null,
        seasonNumber: Int? = 1,
        episodeNumber: Int? = null,
        totalBytes: Long = 0L,
    ) = DownloadItem(
        id = id,
        mediaItemId = "mi-$id",
        name = "Episode $id",
        mediaType = MediaType.EPISODE,
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = totalBytes,
        downloadedBytes = totalBytes,
        status = DownloadStatus.COMPLETED,
        seriesId = seriesId,
        seasonId = seasonId,
        seriesName = seriesName,
        seasonName = seasonName,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
    )

    private fun flat(
        id: String,
        mediaType: MediaType = MediaType.MOVIE,
        name: String = "Item $id",
        totalBytes: Long = 0L,
    ) = DownloadItem(
        id = id,
        mediaItemId = "mi-$id",
        name = name,
        mediaType = mediaType,
        downloadPath = "/data/$id",
        downloadUrl = "https://server/$id",
        totalSizeBytes = totalBytes,
        downloadedBytes = totalBytes,
        status = DownloadStatus.COMPLETED,
    )

    /** The rendered sequence, compressed to readable key strings. */
    private fun shape(rows: List<DownloadListRow>): List<String> = rows.map { row ->
        when (row) {
            is DownloadListRow.SeriesHeader -> "S(${row.name})"
            is DownloadListRow.SeasonHeader -> "SEASON(${row.number ?: row.key})"
            is DownloadListRow.Item -> "I(${row.download.name})"
        }
    }

    private fun itemIds(rows: List<DownloadListRow>): List<String> =
        rows.filterIsInstance<DownloadListRow.Item>().map { it.download.id }

    // ── Numeric ordering (the core gh-175 ask) ────────────────────────────

    @Test
    fun episodes_order_by_number_regardless_of_download_order() {
        // Downloaded E08, then E02, then E10 (DAO list = createdAt DESC).
        val rows = downloadListRows(
            downloads = listOf(
                episode("e8", episodeNumber = 8),
                episode("e2", episodeNumber = 2),
                episode("e10", episodeNumber = 10),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf("series:s1"),
        )
        // E02 first, and E10 sorts after E08 numerically (not lexicographically).
        assertEquals(listOf("e2", "e8", "e10"), itemIds(rows))
    }

    @Test
    fun seasons_order_numerically_s2_before_s10() {
        val rows = downloadListRows(
            downloads = listOf(
                episode("s2e1", seasonNumber = 2, seasonName = "Season 2", episodeNumber = 1),
                episode("s10e1", seasonNumber = 10, seasonName = "Season 10", episodeNumber = 1),
                episode("s1e1", seasonNumber = 1, seasonName = "Season 1", episodeNumber = 1),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf(
                "series:s1",
                "series:s1/season/number:1",
                "series:s1/season/number:2",
                "series:s1/season/number:10",
            ),
        )
        assertEquals(listOf("S(Show)", "SEASON(1)", "I(Episode s1e1)", "SEASON(2)", "I(Episode s2e1)", "SEASON(10)", "I(Episode s10e1)"), shape(rows))
    }

    @Test
    fun missing_episode_numbers_sort_last_within_season() {
        val rows = downloadListRows(
            downloads = listOf(
                episode("e2", episodeNumber = 2),
                episode("e-none", episodeNumber = null),
                episode("e1", episodeNumber = 1),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf("series:s1"),
        )
        assertEquals(listOf("e1", "e2", "e-none"), itemIds(rows))
    }

    // ── Group shape ───────────────────────────────────────────────────────

    @Test
    fun groups_start_collapsed() {
        val rows = downloadListRows(
            downloads = listOf(episode("e1", episodeNumber = 1), episode("e2", episodeNumber = 2)),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = emptySet(),
        )
        assertEquals(listOf("S(Show)"), shape(rows))
    }

    @Test
    fun single_season_lists_episodes_directly_under_the_series() {
        val rows = downloadListRows(
            downloads = listOf(
                episode("e2", episodeNumber = 2),
                episode("e1", episodeNumber = 1),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf("series:s1"),
        )
        assertEquals(listOf("S(Show)", "I(Episode e1)", "I(Episode e2)"), shape(rows))
    }

    @Test
    fun disclosed_group_episodes_carry_the_inside_group_flag() {
        val rows = downloadListRows(
            downloads = listOf(
                episode("e1", episodeNumber = 1),
                flat("m1"),
                episode("x", seriesId = null, seriesName = null, episodeNumber = 1),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf("series:s1"),
        )
        val flagged = rows.filterIsInstance<DownloadListRow.Item>()
            .associate { it.download.id to it.insideGroup }
        // The group member indents; standalone episodes and movies don't.
        assertEquals(true, flagged["e1"])
        assertEquals(false, flagged["x"])
        assertEquals(false, flagged["m1"])
    }

    @Test
    fun multi_season_needs_the_season_expanded_to_disclose_its_episodes() {
        val s1 = "series:s1/season/number:1"
        val s2 = "series:s1/season/number:2"
        val downloads = listOf(
            episode("s2e1", seasonNumber = 2, seasonName = "Season 2", episodeNumber = 1),
            episode("s1e2", seasonNumber = 1, seasonName = "Season 1", episodeNumber = 2),
            episode("s1e1", seasonNumber = 1, seasonName = "Season 1", episodeNumber = 1),
        )

        val seriesOnly = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.RECENT, emptySet())
        assertEquals(listOf("S(Show)"), shape(seriesOnly))

        val seriesExpanded = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.RECENT, setOf("series:s1"))
        assertEquals(listOf("S(Show)", "SEASON(1)", "SEASON(2)"), shape(seriesExpanded))

        val s1Expanded = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.RECENT, setOf("series:s1", s1))
        assertEquals(listOf("S(Show)", "SEASON(1)", "I(Episode s1e1)", "I(Episode s1e2)", "SEASON(2)"), shape(s1Expanded))
        // Season headers carry their own selection ids.
        val seasonRow = s1Expanded.filterIsInstance<DownloadListRow.SeasonHeader>().first()
        assertEquals(listOf("s1e1", "s1e2"), seasonRow.episodeIds)
        assertTrue(s2 in seriesExpanded.map { it.key })
    }

    @Test
    fun legacy_rows_without_series_id_group_by_series_name() {
        val rows = downloadListRows(
            downloads = listOf(
                episode("a", seriesId = null, seriesName = "Show A", episodeNumber = 2),
                episode("b", seriesId = null, seriesName = "Show A", episodeNumber = 1),
                episode("c", seriesId = null, seriesName = "Show B", episodeNumber = 1),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf("series-name:Show A", "series-name:Show B"),
        )
        assertEquals(listOf("S(Show A)", "I(Episode b)", "I(Episode a)", "S(Show B)", "I(Episode c)"), shape(rows))
    }

    @Test
    fun fully_unidentified_rows_stay_standalone() {
        val downloads = listOf(
            episode("x", seriesId = null, seriesName = null, episodeNumber = 1),
            episode("y", seriesId = null, seriesName = null, episodeNumber = 2),
        )
        val rows = downloadListRows(
            downloads = downloads,
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = emptySet(),
        )
        // Flat rows in DAO order — never merged into a bogus "unknown series",
        // and never hidden behind a header tap.
        assertEquals(listOf("I(Episode x)", "I(Episode y)"), shape(rows))

        // They're still episode files, so the Series filter shows them too.
        val seriesOnly = downloadListRows(
            downloads = downloads,
            filter = DownloadListFilter.SERIES,
            sort = DownloadListSort.RECENT,
            expandedKeys = emptySet(),
        )
        assertEquals(listOf("I(Episode x)", "I(Episode y)"), shape(seriesOnly))
    }

    @Test
    fun null_season_numbers_fall_back_to_season_id_then_unknown_bucket() {
        val rows = downloadListRows(
            downloads = listOf(
                // Same seasonId → one season even without numbers.
                episode("a", seasonId = "se9", seasonNumber = null, episodeNumber = 1),
                episode("b", seasonId = "se9", seasonNumber = null, episodeNumber = 2),
                // No season identity at all → its own "unknown" bucket.
                episode("c", seasonId = null, seasonNumber = null, episodeNumber = 1),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf("series:s1"),
        )
        val seasons = rows.filterIsInstance<DownloadListRow.SeasonHeader>()
        assertEquals(2, seasons.size)
        // The identified season (by id) sorts before the unknown bucket.
        assertEquals("id:se9", seasons.first().key.substringAfterLast('/'))
        assertEquals(listOf("a", "b"), seasons.first().episodeIds)
        assertEquals("unknown", seasons.last().key.substringAfterLast('/'))
    }

    @Test
    fun numberless_legacy_seasons_order_digit_aware_by_name_s2_before_s10() {
        val rows = downloadListRows(
            downloads = listOf(
                episode("s10e1", seasonId = "se10", seasonNumber = null, seasonName = "Season 10", episodeNumber = 1),
                episode("s2e1", seasonId = "se2", seasonNumber = null, seasonName = "Season 2", episodeNumber = 1),
            ),
            filter = DownloadListFilter.ALL,
            sort = DownloadListSort.RECENT,
            expandedKeys = setOf("series:s1"),
        )
        val seasons = rows.filterIsInstance<DownloadListRow.SeasonHeader>()
        // Lexicographic order would put "Season 10" first; the digit-aware
        // fallback keeps the numeric order the numbered seasons get.
        assertEquals(listOf("Season 2", "Season 10"), seasons.map { it.name })
    }

    @Test
    fun compareNatural_orders_digit_runs_numerically() {
        assertTrue(compareNatural("Season 2", "Season 10") < 0)
        assertEquals(0, compareNatural("Season 2", "Season 2"))
        assertTrue(compareNatural("Season 10", "Season 2") > 0)
        assertTrue(compareNatural("Season 1", "Season 1 Part 2") < 0)
        assertTrue(compareNatural("Alpha", "Beta") < 0)
    }

    // ── Filter partition ──────────────────────────────────────────────────

    @Test
    fun movies_filter_shows_only_movies_and_series_filter_only_episode_groups() {
        val downloads = listOf(
            flat("movie", name = "Film"),
            episode("e1", episodeNumber = 1),
            flat("song", mediaType = MediaType.AUDIO, name = "Song"),
        )
        val movies = downloadListRows(downloads, DownloadListFilter.MOVIES, DownloadListSort.RECENT, emptySet())
        assertEquals(listOf("I(Film)"), shape(movies))

        val series = downloadListRows(downloads, DownloadListFilter.SERIES, DownloadListSort.RECENT, setOf("series:s1"))
        assertEquals(listOf("S(Show)", "I(Episode e1)"), shape(series))

        // Music/books are only reachable under All. The group takes its newest
        // episode's slot (idx 1) — after the newer movie, before the song.
        val all = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.RECENT, setOf("series:s1"))
        assertEquals(listOf("I(Film)", "S(Show)", "I(Episode e1)", "I(Song)"), shape(all))
    }

    // ── Sorts ─────────────────────────────────────────────────────────────

    @Test
    fun recent_sort_interleaves_groups_and_flat_rows_by_dao_recency() {
        val downloads = listOf(
            episode("epNew", seriesId = "sA", seriesName = "Show A", episodeNumber = 1), // idx 0 — newest
            flat("film", name = "Film"),                                                  // idx 1
            episode("epOld", seriesId = "sA", seriesName = "Show A", episodeNumber = 2), // idx 2
        )
        val rows = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.RECENT, setOf("series:sA"))
        // The group takes its NEWEST episode's slot; inner order is playback order.
        assertEquals(listOf("S(Show A)", "I(Episode epNew)", "I(Episode epOld)", "I(Film)"), shape(rows))
    }

    @Test
    fun name_sort_is_case_insensitive_and_never_scrambles_inner_episode_order() {
        val downloads = listOf(
            episode("z8", seriesId = "sZ", seriesName = "Zeta", episodeNumber = 8),
            flat("m", name = "middle"),
            episode("z2", seriesId = "sZ", seriesName = "Zeta", episodeNumber = 2),
            episode("a1", seriesId = "sA", seriesName = "alpha", episodeNumber = 1),
        )
        val rows = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.NAME, setOf("series:sZ", "series:sA"))
        assertEquals(
            listOf("S(alpha)", "I(Episode a1)", "I(middle)", "S(Zeta)", "I(Episode z2)", "I(Episode z8)"),
            shape(rows),
        )
    }

    @Test
    fun size_sort_orders_groups_by_total_bytes_descending() {
        val downloads = listOf(
            episode("small", seriesId = "sS", seriesName = "Small", episodeNumber = 1, totalBytes = 50),
            flat("big", name = "Big", totalBytes = 300),
            episode("midA", seriesId = "sM", seriesName = "Mid", episodeNumber = 1, totalBytes = 100),
            episode("midB", seriesId = "sM", seriesName = "Mid", episodeNumber = 2, totalBytes = 100),
        )
        val rows = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.SIZE, emptySet())
        assertEquals(listOf("I(Big)", "S(Mid)", "S(Small)"), shape(rows))
    }

    // ── Tri-state group selection ─────────────────────────────────────────

    @Test
    fun group_selection_state_reads_off_on_and_partial() {
        val ids = listOf("a", "b")
        assertEquals(ToggleableState.Off, groupSelectionState(ids, emptySet()))
        assertEquals(ToggleableState.On, groupSelectionState(ids, setOf("a", "b")))
        assertEquals(ToggleableState.Indeterminate, groupSelectionState(ids, setOf("a")))
        assertEquals(ToggleableState.Off, groupSelectionState(emptyList(), setOf("a")))
    }

    @Test
    fun toggled_group_selection_selects_all_then_clears_the_group() {
        val selected = mutableSetOf("other")
        val first = toggledGroupSelection(selected, listOf("a", "b"))
        assertEquals(setOf("other", "a", "b"), first)

        // Partial → completes the group.
        val completed = toggledGroupSelection(setOf("other", "a"), listOf("a", "b"))
        assertEquals(setOf("other", "a", "b"), completed)

        // Fully selected → clears exactly the group.
        val cleared = toggledGroupSelection(first, listOf("a", "b"))
        assertEquals(setOf("other"), cleared)

        // Empty group ids are a no-op.
        assertEquals(setOf("other"), toggledGroupSelection(selected, emptyList()))
    }

    // ── Header summaries (size + completed count) ─────────────────────────

    @Test
    fun series_header_carries_total_bytes_and_completed_count() {
        val downloads = listOf(
            episode("e1", episodeNumber = 1, totalBytes = 100),
            episode("e2", episodeNumber = 2, totalBytes = 250),
        ).let { list ->
            // Mark the second episode as still in flight: downloadedBytes≠total,
            // status DOWNLOADING.
            list.mapIndexed { index, item ->
                if (index == 1) item.copy(status = DownloadStatus.DOWNLOADING, downloadedBytes = 50) else item
            }
        }
        val rows = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.RECENT, emptySet())
        val header = rows.filterIsInstance<DownloadListRow.SeriesHeader>().single()
        assertEquals(350L, header.totalBytes)
        assertEquals(1, header.completedCount)
        assertEquals(2, header.episodeCount)
    }

    @Test
    fun season_headers_carry_their_own_slice_of_bytes_and_completions() {
        val downloads = listOf(
            episode("s1e1", seasonNumber = 1, seasonName = "Season 1", episodeNumber = 1, totalBytes = 100),
            episode("s2e1", seasonNumber = 2, seasonName = "Season 2", episodeNumber = 1, totalBytes = 200),
            episode("s2e2", seasonNumber = 2, seasonName = "Season 2", episodeNumber = 2, totalBytes = 300),
        )
        val rows = downloadListRows(downloads, DownloadListFilter.ALL, DownloadListSort.RECENT, setOf("series:s1"))
        val seasons = rows.filterIsInstance<DownloadListRow.SeasonHeader>()
        assertEquals(2, seasons.size)
        assertEquals(100L, seasons[0].totalBytes)
        assertEquals(1, seasons[0].completedCount)
        assertEquals(500L, seasons[1].totalBytes)
        assertEquals(2, seasons[1].completedCount)
        // The series header aggregates both seasons.
        val series = rows.filterIsInstance<DownloadListRow.SeriesHeader>().single()
        assertEquals(600L, series.totalBytes)
        assertEquals(3, series.completedCount)
    }

    // ── Group play target ─────────────────────────────────────────────────

    @Test
    fun group_play_target_picks_the_first_unplayed_in_playback_order() {
        val group = listOf(
            episode("e1", episodeNumber = 1),
            episode("e2", episodeNumber = 2),
            episode("e3", episodeNumber = 3),
        )
        val target = groupPlayTarget(group, playedItemIds = setOf("mi-e1", "mi-e2"))
        assertEquals("e3", target?.id)
    }

    @Test
    fun group_play_target_falls_back_to_first_when_everything_is_watched() {
        val group = listOf(
            episode("e1", episodeNumber = 1),
            episode("e2", episodeNumber = 2),
        )
        val target = groupPlayTarget(group, playedItemIds = setOf("mi-e1", "mi-e2"))
        assertEquals("e1", target?.id)
    }

    @Test
    fun group_play_target_on_an_empty_group_is_null() {
        assertEquals(null, groupPlayTarget(emptyList(), playedItemIds = emptySet()))
    }

    @Test
    fun group_play_target_skips_episodes_that_are_not_completed() {
        // E1 finished first but is watched; E3 is still queued, so the header
        // Play must land on the unwatched COMPLETED E2 — never on a row whose
        // file isn't on disk.
        val group = listOf(
            episode("e1", episodeNumber = 1),
            episode("e2", episodeNumber = 2),
            episode("e3", episodeNumber = 3).copy(status = DownloadStatus.QUEUED),
        )
        val target = groupPlayTarget(group, playedItemIds = setOf("mi-e1"))
        assertEquals("e2", target?.id)
    }

    @Test
    fun group_play_target_falls_back_to_first_completed_not_first_row() {
        val group = listOf(
            episode("e1", episodeNumber = 1),
            episode("e2", episodeNumber = 2),
            episode("e3", episodeNumber = 3).copy(status = DownloadStatus.DOWNLOADING),
        )
        val target = groupPlayTarget(group, playedItemIds = setOf("mi-e1", "mi-e2"))
        assertEquals("e1", target?.id)
    }

    @Test
    fun group_play_target_is_null_when_nothing_in_the_group_is_completed() {
        val group = listOf(
            episode("e1", episodeNumber = 1).copy(status = DownloadStatus.QUEUED),
            episode("e2", episodeNumber = 2).copy(status = DownloadStatus.PAUSED),
        )
        assertEquals(null, groupPlayTarget(group, playedItemIds = emptySet()))
    }

    // ── Persisted-value decode ────────────────────────────────────────────

    @Test
    fun filter_and_sort_names_decode_tolerantly() {
        assertEquals(DownloadListFilter.SERIES, DownloadListFilter.fromName("SERIES"))
        assertEquals(DownloadListFilter.ALL, DownloadListFilter.fromName(null))
        assertEquals(DownloadListFilter.ALL, DownloadListFilter.fromName(""))
        assertEquals(DownloadListFilter.ALL, DownloadListFilter.fromName("LEGACY_GONE"))

        assertEquals(DownloadListSort.SIZE, DownloadListSort.fromName("SIZE"))
        assertEquals(DownloadListSort.RECENT, DownloadListSort.fromName(null))
        assertEquals(DownloadListSort.RECENT, DownloadListSort.fromName("WHATEVER"))
    }

    // ── Series header artwork ─────────────────────────────────────────────

    @Test
    fun series_header_prefers_the_saved_poster_and_falls_back_to_episode_stills() {
        val rows = downloadListRows(
            listOf(
                episode("ep1").copy(imageUrl = "still-ep1", imageBlurHash = "bh1"),
                episode("ep2", seriesId = "s2", seriesName = "Other").copy(imageUrl = "still-ep2"),
                // A series with no saved poster and no stills renders artwork-less.
                episode("ep3", seriesId = "s3", seriesName = "Bare"),
            ),
            DownloadListFilter.ALL,
            DownloadListSort.RECENT,
            emptySet(),
            seriesArt = mapOf("s1" to SeriesArt("poster-s1", "poster-bh")),
        )
        val headers = rows.filterIsInstance<DownloadListRow.SeriesHeader>()
        val s1 = headers.first { it.key == "series:s1" }
        assertEquals("poster-s1", s1.imageUrl)
        assertEquals("poster-bh", s1.imageBlurHash)
        val s2 = headers.first { it.key == "series:s2" }
        assertEquals("still-ep2", s2.imageUrl)
        assertNull(s2.imageBlurHash)
        val s3 = headers.first { it.key == "series:s3" }
        assertNull(s3.imageUrl)
    }

    @Test
    fun still_fallback_prefers_a_completed_episode() {
        val rows = downloadListRows(
            listOf(
                episode("epA", episodeNumber = 1).copy(status = DownloadStatus.DOWNLOADING, imageUrl = "still-downloading"),
                episode("epB", episodeNumber = 2).copy(imageUrl = "still-completed"),
            ),
            DownloadListFilter.ALL,
            DownloadListSort.RECENT,
            emptySet(),
        )
        val header = rows.filterIsInstance<DownloadListRow.SeriesHeader>().single()
        assertEquals("still-completed", header.imageUrl)
    }

    // ── Aggregate transfer fraction (collapsed headers + app-bar ring) ────

    @Test
    fun aggregate_fraction_is_null_when_no_item_qualifies() {
        val items = listOf(
            episode("ep1").copy(status = DownloadStatus.PAUSED),
            episode("ep2"),
        )
        assertNull(aggregateProgressFraction(items, setOf(DownloadStatus.DOWNLOADING), emptyMap()))
    }

    @Test
    fun collapsed_group_fraction_is_null_without_an_actively_downloading_episode() {
        // Paused + completed-only groups hide the header's aggregate bar —
        // "downloads in progress" gates it.
        val items = listOf(
            episode("ep1").copy(status = DownloadStatus.PAUSED, downloadedBytes = 200),
            episode("ep2"),
        )
        assertNull(downloadGroupProgressFraction(items, emptyMap()))
    }

    @Test
    fun collapsed_group_fraction_blends_completed_structural_and_live_bytes() {
        // ep1 landed whole (500/500); ep2 is half-way by LIVE bytes (250/500)
        // — its stale structural bytes are overridden by the live value — and
        // ep3's unprobed total contributes to neither sum: 750/1000.
        val items = listOf(
            episode("ep1", totalBytes = 500),
            episode("ep2", totalBytes = 500).copy(status = DownloadStatus.DOWNLOADING, downloadedBytes = 100),
            episode("ep3").copy(status = DownloadStatus.PENDING),
        )
        val fraction = downloadGroupProgressFraction(
            items,
            mapOf("ep2" to DownloadProgress(id = "ep2", downloadedBytes = 250, speedBytesPerSec = 0, totalSizeBytes = 500)),
        )
        assertEquals(0.75f, fraction)
    }

    @Test
    fun aggregate_fraction_excludes_items_outside_the_status_set_and_unknown_totals() {
        // A paused row (outside the DOWNLOADING gate) and a pending row with
        // no probed size yet contribute to neither sum — only ep3's 100/400
        // shapes the fraction.
        val items = listOf(
            episode("ep1", totalBytes = 400).copy(status = DownloadStatus.PAUSED),
            episode("ep2").copy(status = DownloadStatus.PENDING),
            episode("ep3", totalBytes = 400).copy(status = DownloadStatus.DOWNLOADING, downloadedBytes = 100),
        )
        val fraction = aggregateProgressFraction(items, setOf(DownloadStatus.DOWNLOADING), emptyMap())
        assertEquals(0.25f, fraction)
    }

    @Test
    fun aggregate_fraction_covers_the_whole_active_set_for_the_app_bar_ring() {
        // The app-bar ring gates on the Active set: a paused row counts with
        // its structural bytes, a queued row without a probed size costs
        // nothing — (300 + 150) / 1200.
        val items = listOf(
            episode("ep1", totalBytes = 600).copy(status = DownloadStatus.PAUSED, downloadedBytes = 300),
            episode("ep2", totalBytes = 600).copy(status = DownloadStatus.DOWNLOADING, downloadedBytes = 150),
            episode("ep3").copy(status = DownloadStatus.QUEUED),
        )
        val fraction = aggregateProgressFraction(items, DownloadStatusFilter.ACTIVE_STATUSES, emptyMap())
        assertEquals(0.375f, fraction)
    }
}
