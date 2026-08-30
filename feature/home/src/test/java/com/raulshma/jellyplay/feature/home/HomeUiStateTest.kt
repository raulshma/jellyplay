package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the state classes' load-bearing DEFAULTS only (the cold-screen spinner,
 * the offline-online start mode, the empty search surface). The previous
 * ~150-line remainder asserted data-class `copy` and stdlib
 * `coerceAtLeast` semantics — the language, not an interface — and taught
 * readers to distrust the package's suites.
 */
class HomeUiStateTest {

    @Test
    fun defaultState_hasCorrectDefaults() {
        val state = HomeUiState()
        assertTrue(state.sections.isEmpty())
        assertTrue(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertEquals(HomeMode.VIDEO, state.homeMode)
        assertTrue(state.appearance.dynamicTheming)
        assertFalse(state.appearance.oledMode)
        assertFalse(state.newsletterBannerVisible)
        assertFalse(state.isSearchActive)
    }

    @Test
    fun defaultSearchState_isEmpty() {
        val state = HomeSearchState()
        assertTrue(state.jellyfinResults.isEmpty())
        assertTrue(state.seerrResults.isEmpty())
        assertFalse(state.isSearching)
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
    fun defaultScrollPosition_isZero() {
        val pos = HomeScrollPosition()
        assertEquals(0, pos.firstVisibleItemIndex)
        assertEquals(0, pos.firstVisibleItemScrollOffset)
    }
}
