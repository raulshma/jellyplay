package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the single offline-render predicate ([computeHomeRenderSource]) — the
 * fold every home surface (screen branches, the VM's downloads-rendering
 * gate) reads instead of re-deriving its own copy.
 */
class HomeRenderSourceTest {

    private val library = listOf(OfflineMediaItem(id = "d1", name = "Downloaded", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE))

    @Test
    fun explicitOffline_wins_regardlessOfFetchState() {
        for (fetchFailedEmpty in listOf(false, true)) {
            assertEquals(
                HomeRenderSource.Offline,
                computeHomeRenderSource(OfflineMode.OFFLINE_MANUAL, fetchFailedEmpty, emptyList(), fallbackPending = false),
            )
        }
    }

    @Test
    fun healthyOnlineFetch_rendersOnline_evenWithStaleLibrary() {
        assertEquals(
            HomeRenderSource.Online,
            computeHomeRenderSource(OfflineMode.ONLINE, fetchFailedEmpty = false, offlineLibrary = library, fallbackPending = false),
        )
    }

    @Test
    fun failedFetch_beforeFirstEmission_isFallbackPending() {
        assertEquals(
            HomeRenderSource.FallbackPending,
            computeHomeRenderSource(OfflineMode.ONLINE, fetchFailedEmpty = true, offlineLibrary = emptyList(), fallbackPending = true),
        )
    }

    @Test
    fun failedFetch_withDownloads_isImplicitOffline() {
        assertEquals(
            HomeRenderSource.Offline,
            computeHomeRenderSource(OfflineMode.ONLINE, fetchFailedEmpty = true, offlineLibrary = library, fallbackPending = false),
        )
    }

    @Test
    fun failedFetch_confirmedEmpty_rendersOnline_hardError() {
        assertEquals(
            HomeRenderSource.Online,
            computeHomeRenderSource(OfflineMode.ONLINE, fetchFailedEmpty = true, offlineLibrary = emptyList(), fallbackPending = false),
        )
    }
}
