package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.QuickAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the PRODUCTION quick-action routing table
 * ([homeQuickActionEffect]) — the series-vs-movie sheet/dialog/download
 * decisions that used to be welded inside a remembered composable lambda.
 */
class HomeQuickActionsTest {

    private fun item(type: MediaType) = MediaItem(id = "i1", name = "Item", mediaType = type)

    @Test
    fun play_routesToPlayFunnel_forEveryType() {
        assertEquals(
            HomeQuickActionEffect.Play(item(MediaType.SERIES)),
            homeQuickActionEffect(item(MediaType.SERIES), QuickAction.PLAY, noopNavigate()),
        )
        assertEquals(
            HomeQuickActionEffect.Play(item(MediaType.MOVIE)),
            homeQuickActionEffect(item(MediaType.MOVIE), QuickAction.PLAY, noopNavigate()),
        )
    }

    @Test
    fun markWatchedAndUnwatched_carryThePlayedFlag() {
        val movie = item(MediaType.MOVIE)

        assertEquals(
            HomeQuickActionEffect.MarkPlayed(movie, played = true),
            homeQuickActionEffect(movie, QuickAction.MARK_WATCHED, noopNavigate()),
        )
        assertEquals(
            HomeQuickActionEffect.MarkPlayed(movie, played = false),
            homeQuickActionEffect(movie, QuickAction.MARK_UNWATCHED, noopNavigate()),
        )
    }

    @Test
    fun details_opensTheUnifiedDetailTree() {
        assertEquals(
            HomeQuickActionEffect.ShowDetails(item(MediaType.MOVIE)),
            homeQuickActionEffect(item(MediaType.MOVIE), QuickAction.DETAILS, noopNavigate()),
        )
    }

    @Test
    fun download_series_opensTheSeriesSheet() {
        val series = item(MediaType.SERIES)

        assertEquals(
            HomeQuickActionEffect.OpenSeriesDownloadSheet(series),
            homeQuickActionEffect(series, QuickAction.DOWNLOAD, noopNavigate()),
        )
    }

    @Test
    fun download_nonSeries_startsInlineWithTheNavigationCallback() {
        val movie = item(MediaType.MOVIE)
        val onOpenDetail: (String, Boolean) -> Unit = { _, _ -> }

        val effect = homeQuickActionEffect(movie, QuickAction.DOWNLOAD, onOpenDetail)

        assertEquals(HomeQuickActionEffect.StartDownload(movie, onOpenDetail), effect)
    }

    @Test
    fun removeDownload_series_opensTheDeleteEpisodesSheet() {
        val series = item(MediaType.SERIES)

        assertEquals(
            HomeQuickActionEffect.OpenSeriesDeleteSheet(series),
            homeQuickActionEffect(series, QuickAction.REMOVE_DOWNLOAD, noopNavigate()),
        )
    }

    @Test
    fun removeDownload_nonSeries_asksForConfirmation() {
        assertEquals(
            HomeQuickActionEffect.ConfirmDeleteDownload(item(MediaType.MOVIE)),
            homeQuickActionEffect(item(MediaType.MOVIE), QuickAction.REMOVE_DOWNLOAD, noopNavigate()),
        )
        assertEquals(
            HomeQuickActionEffect.ConfirmDeleteDownload(item(MediaType.AUDIO)),
            homeQuickActionEffect(item(MediaType.AUDIO), QuickAction.REMOVE_DOWNLOAD, noopNavigate()),
        )
    }

    private fun noopNavigate(): (String, Boolean) -> Unit = { _, _ -> }
}
