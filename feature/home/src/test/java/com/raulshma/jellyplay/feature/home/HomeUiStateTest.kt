package com.raulshma.jellyplay.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {

    @Test
    fun defaultState_hasCorrectDefaults() {
        val state = HomeUiState()
        assertTrue(state.sections.isEmpty())
        assertTrue(state.favorites.isEmpty())
        assertTrue(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertEquals(com.raulshma.jellyplay.core.model.HomeMode.VIDEO, state.homeMode)
        assertTrue(state.dynamicTheming)
        assertFalse(state.oledMode)
        assertFalse(state.newsletterBannerVisible)
    }

    @Test
    fun defaultSearchState_hasEmptyQuery() {
        val state = HomeSearchState()
        assertEquals("", state.query)
        assertTrue(state.jellyfinResults.isEmpty())
        assertTrue(state.seerrResults.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun defaultSeerrRequestState_hasNoItem() {
        val state = SeerrRequestState()
        assertNull(state.requestItem)
        assertNull(state.result)
        assertTrue(state.radarrServers.isEmpty())
        assertTrue(state.sonarrServers.isEmpty())
        assertFalse(state.isLoadingServices)
        assertTrue(state.tvSeasons.isEmpty())
    }

    @Test
    fun defaultScrollPosition_isZero() {
        val pos = HomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }

    @Test
    fun defaultFocusPosition_isZero() {
        val pos = HomeFocusPosition()
        assertEquals(0, pos.sectionIndex)
        assertEquals(0, pos.itemIndex)
    }
}
