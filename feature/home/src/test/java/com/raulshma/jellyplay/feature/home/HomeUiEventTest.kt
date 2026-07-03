package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiEventTest {

    @Test
    fun refresh_isHomeUiEvent() {
        val event: HomeUiEvent = HomeUiEvent.Refresh
        assertTrue(event is HomeUiEvent.Refresh)
    }

    @Test
    fun pullToRefresh_isHomeUiEvent() {
        val event: HomeUiEvent = HomeUiEvent.PullToRefresh
        assertTrue(event is HomeUiEvent.PullToRefresh)
    }

    @Test
    fun toggleOfflineMode_isHomeUiEvent() {
        val event: HomeUiEvent = HomeUiEvent.ToggleOfflineMode
        assertTrue(event is HomeUiEvent.ToggleOfflineMode)
    }

    @Test
    fun clearSearch_isHomeUiEvent() {
        val event: HomeUiEvent = HomeUiEvent.ClearSearch
        assertTrue(event is HomeUiEvent.ClearSearch)
    }

    @Test
    fun clearRequestResult_isHomeUiEvent() {
        val event: HomeUiEvent = HomeUiEvent.ClearRequestResult
        assertTrue(event is HomeUiEvent.ClearRequestResult)
    }

    @Test
    fun dismissNewsletterBanner_isHomeUiEvent() {
        val event: HomeUiEvent = HomeUiEvent.DismissNewsletterBanner
        assertTrue(event is HomeUiEvent.DismissNewsletterBanner)
    }

    @Test
    fun updateSearchQuery_holdsQuery() {
        val event = HomeUiEvent.UpdateSearchQuery("batman")
        assertEquals("batman", event.query)
    }

    @Test
    fun updateSearchQuery_emptyString() {
        val event = HomeUiEvent.UpdateSearchQuery("")
        assertEquals("", event.query)
    }

    @Test
    fun selectSeerrRequestItem_holdsItem() {
        val item = makeSeerrItem(id = 42, title = "Batman")
        val event = HomeUiEvent.SelectSeerrRequestItem(item)
        assertNotNull(event.item)
        assertEquals(42, event.item!!.id)
    }

    @Test
    fun selectSeerrRequestItem_nullItem() {
        val event = HomeUiEvent.SelectSeerrRequestItem(null)
        assertNull(event.item)
    }

    @Test
    fun loadSeerrServiceDetails_holdsMediaType() {
        val event = HomeUiEvent.LoadSeerrServiceDetails("movie")
        assertEquals("movie", event.mediaType)
    }

    @Test
    fun loadTvSeasons_holdsTmdbId() {
        val event = HomeUiEvent.LoadTvSeasons(tmdbId = 12345)
        assertEquals(12345, event.tmdbId)
    }

    @Test
    fun requestSeerrMedia_holdsRequiredFields() {
        val item = makeSeerrItem(id = 1, title = "Test")
        val event = HomeUiEvent.RequestSeerrMedia(item = item)
        assertEquals(1, event.item.id)
        assertNull(event.seasons)
        assertNull(event.serverId)
        assertNull(event.profileId)
        assertNull(event.rootFolder)
        assertNull(event.tags)
    }

    @Test
    fun requestSeerrMedia_holdsOptionalSeasons() {
        val item = makeSeerrItem(id = 2, title = "Series")
        val event = HomeUiEvent.RequestSeerrMedia(
            item = item,
            seasons = listOf(1, 2, 3),
            serverId = 10,
            profileId = 5,
            rootFolder = "/media",
            tags = listOf(7),
        )
        assertEquals(listOf(1, 2, 3), event.seasons)
        assertEquals(10, event.serverId)
        assertEquals(5, event.profileId)
        assertEquals("/media", event.rootFolder)
        assertEquals(listOf(7), event.tags)
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun makeSeerrItem(id: Int, title: String) = SeerrSearchItem(
        id = id,
        mediaType = "movie",
        title = title,
    )
}
