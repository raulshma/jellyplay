package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pins the single offline-render predicate ([computeHomeRenderSource]) — the
 * fold every home surface (screen branches, the VM's downloads-rendering
 * gate) reads instead of re-deriving its own copy. The two equivalence
 * tests below pin BOTH directions of
 * `offlineMode != ONLINE ⟺ renderSource == Offline.Explicit` — the license
 * `homeSurface` relies on to read `renderSource` alone.
 */
class HomeRenderSourceTest {

    private val library = listOf(OfflineMediaItem(id = "d1", name = "Downloaded", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE))

    @Test
    fun everyNonOnlineMode_isExplicitOffline() {
        // A populated library in EVERY iteration: explicit mode must win even
        // when downloads exist — otherwise an implicit-before-explicit branch
        // reorder in computeHomeRenderSource would slip through.
        for (mode in OfflineMode.entries.filter { it != OfflineMode.ONLINE }) {
            for (fetchFailedEmpty in listOf(false, true)) {
                for (fallbackPending in listOf(false, true)) {
                    for (offlineLibrary in listOf(emptyList(), library)) {
                        assertEquals(
                            HomeRenderSource.Offline.Explicit,
                            computeHomeRenderSource(mode, fetchFailedEmpty, offlineLibrary, fallbackPending),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun onlineMode_neverYieldsExplicitOffline() {
        for (fetchFailedEmpty in listOf(false, true)) {
            for (fallbackPending in listOf(false, true)) {
                for (offlineLibrary in listOf(emptyList(), library)) {
                    assertFalse(
                        computeHomeRenderSource(OfflineMode.ONLINE, fetchFailedEmpty, offlineLibrary, fallbackPending)
                            is HomeRenderSource.Offline.Explicit,
                    )
                }
            }
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
            HomeRenderSource.Offline.Implicit,
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
