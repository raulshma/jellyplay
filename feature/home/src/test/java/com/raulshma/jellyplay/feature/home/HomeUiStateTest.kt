package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.seerr.SeerrRequestSnapshot
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OfflineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {

    @Test
    fun defaultState_hasCorrectDefaults() {
        val state = HomeUiState()
        assertTrue(state.sections.isEmpty())
        assertTrue(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertEquals(HomeMode.VIDEO, state.homeMode)
        assertTrue(state.dynamicTheming)
        assertFalse(state.oledMode)
        assertFalse(state.newsletterBannerVisible)
    }

    @Test
    fun copy_updatesIsLoading() {
        val state = HomeUiState().copy(isLoading = false)
        assertFalse(state.isLoading)
    }

    @Test
    fun copy_updatesIsRefreshing() {
        val state = HomeUiState().copy(isRefreshing = true)
        assertTrue(state.isRefreshing)
    }

    @Test
    fun copy_updatesError() {
        val state = HomeUiState().copy(error = "Network error")
        assertEquals("Network error", state.error)
    }

    @Test
    fun copy_clearsError() {
        val state = HomeUiState(error = "oops").copy(error = null)
        assertNull(state.error)
    }

    @Test
    fun copy_updatesHomeMode() {
        val state = HomeUiState().copy(homeMode = HomeMode.MUSIC)
        assertEquals(HomeMode.MUSIC, state.homeMode)
    }

    @Test
    fun copy_updatesOfflineMode() {
        val state = HomeUiState().copy(offlineMode = OfflineMode.OFFLINE_MANUAL)
        assertEquals(OfflineMode.OFFLINE_MANUAL, state.offlineMode)
    }

    @Test
    fun copy_updatesDiscoverEnabled() {
        val state = HomeUiState().copy(discoverEnabled = true)
        assertTrue(state.discoverEnabled)
    }

    @Test
    fun copy_updatesNewsletterBannerVisible() {
        val state = HomeUiState().copy(newsletterBannerVisible = true)
        assertTrue(state.newsletterBannerVisible)
    }

    @Test
    fun copy_updatesCurrentUser() {
        val user = com.raulshma.jellyplay.core.model.UserInfo(
            id = "u1",
            name = "Alice",
            serverAddress = "http://localhost",
            accessToken = "token",
            serverId = "s1",
            primaryImageTag = null
        )
        val state = HomeUiState().copy(currentUser = user)
        assertNotNull(state.currentUser)
        assertEquals("u1", state.currentUser!!.id)
    }

    @Test
    fun defaultSearchState_isEmpty() {
        val state = HomeSearchState()
        assertTrue(state.jellyfinResults.isEmpty())
        assertTrue(state.seerrResults.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun searchState_copy_updatesIsSearching() {
        val state = HomeSearchState().copy(isSearching = true)
        assertTrue(state.isSearching)
    }

    @Test
    fun defaultUiState_isSearchActiveIsFalse() {
        val state = HomeUiState()
        assertFalse(state.isSearchActive)
    }

    @Test
    fun uiState_copy_updatesIsSearchActive() {
        val state = HomeUiState().copy(isSearchActive = true)
        assertTrue(state.isSearchActive)
    }

    @Test
    fun searchState_isSearching_falseWhenQueryBlank() {
        val query = "  "
        val isSearching = query.isNotBlank()
        assertFalse(isSearching)
    }

    @Test
    fun searchState_isSearching_trueWhenQueryNonBlank() {
        val query = "hello"
        val isSearching = query.isNotBlank()
        assertTrue(isSearching)
    }

    @Test
    fun defaultSeerrRequestState_hasNoItem() {
        val state = SeerrRequestState()
        assertNull(state.requestItem)
        assertNull(state.snapshot.requestResult)
        assertTrue(state.snapshot.radarrServers.isEmpty())
        assertTrue(state.snapshot.sonarrServers.isEmpty())
        assertFalse(state.snapshot.isLoadingServices)
        assertTrue(state.snapshot.tvSeasons.isEmpty())
    }

    @Test
    fun seerrRequestState_copy_updatesIsLoadingServices() {
        val state = SeerrRequestState().copy(snapshot = SeerrRequestSnapshot(isLoadingServices = true))
        assertTrue(state.snapshot.isLoadingServices)
    }

    @Test
    fun seerrRequestState_copy_clearsResult() {
        val withResult = SeerrRequestState(
            snapshot = SeerrRequestSnapshot(
                requestResult = com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult(success = true),
            ),
        )
        val state = withResult.copy(snapshot = withResult.snapshot.copy(requestResult = null))
        assertNull(state.snapshot.requestResult)
    }

    @Test
    fun defaultScrollPosition_isZero() {
        val pos = HomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollPosition_coerceAtLeast_positivesPassThrough() {
        val index = 5.coerceAtLeast(0)
        val offset = 100.coerceAtLeast(0)
        assertEquals(5, index)
        assertEquals(100, offset)
    }

    @Test
    fun scrollPosition_coerceAtLeast_negativesClampToZero() {
        val index = (-3).coerceAtLeast(0)
        val offset = (-99).coerceAtLeast(0)
        assertEquals(0, index)
        assertEquals(0, offset)
    }

    @Test
    fun scrollPosition_coerceAtLeast_zeroStaysZero() {
        val index = 0.coerceAtLeast(0)
        assertEquals(0, index)
    }

    @Test
    fun scrollPosition_dataClass_equality() {
        val a = HomeScrollPosition(3, 50)
        val b = HomeScrollPosition(3, 50)
        assertEquals(a, b)
    }
}
