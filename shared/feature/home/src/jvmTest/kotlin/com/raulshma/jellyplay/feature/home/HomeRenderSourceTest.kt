package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.Test

/**
 * Pins the single offline-render predicate ([computeHomeRenderSource]) — the
 * fold every home surface (screen branches, the VM's downloads-rendering
 * gate) reads instead of re-deriving its own copy. The two equivalence
 * tests below pin BOTH directions of
 * `offlineMode != ONLINE ⟺ renderSource == Offline.Explicit` — the license
 * `homeSurface` relies on to read `renderSource` alone.
 *
 * The healthy-direction tests iterate EVERY non-[HomeLoadPhase.Failed]
 * variant: the fold's exhaustive `when` routes all of them to Online, and a
 * new phase variant added later must declare its treatment here to compile.
 */
class HomeRenderSourceTest {

    private val library = listOf(OfflineMediaItem(id = "d1", name = "Downloaded", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE))

    /** Every non-Failed phase — all render Online in online mode. */
    private val healthyPhases = listOf(
        HomeLoadPhase.ColdLoading,
        HomeLoadPhase.Ready,
        HomeLoadPhase.InlineRefreshing,
        HomeLoadPhase.Handshaking,
    )

    private fun failed(fetchFailed: Boolean): HomeLoadPhase =
        if (fetchFailed) HomeLoadPhase.Failed("server down") else HomeLoadPhase.Ready

    @Test
    fun everyNonOnlineMode_isExplicitOffline() {
        // A populated library in EVERY iteration: explicit mode must win even
        // when downloads exist — otherwise an implicit-before-explicit branch
        // reorder in computeHomeRenderSource would slip through.
        for (mode in OfflineMode.entries.filter { it != OfflineMode.ONLINE }) {
            for (phase in healthyPhases + HomeLoadPhase.Failed("boom")) {
                for (fallbackPending in listOf(false, true)) {
                    for (offlineLibrary in listOf(emptyList(), library)) {
                        assertEquals(
                            HomeRenderSource.Offline.Explicit,
                            computeHomeRenderSource(mode, phase, offlineLibrary, fallbackPending),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun onlineMode_neverYieldsExplicitOffline() {
        for (phase in healthyPhases + HomeLoadPhase.Failed("boom")) {
            for (fallbackPending in listOf(false, true)) {
                for (offlineLibrary in listOf(emptyList(), library)) {
                    assertFalse(
                        computeHomeRenderSource(OfflineMode.ONLINE, phase, offlineLibrary, fallbackPending)
                            is HomeRenderSource.Offline.Explicit,
                    )
                }
            }
        }
    }

    @Test
    fun healthyOnlineFetch_rendersOnline_evenWithStaleLibrary() {
        for (phase in healthyPhases) {
            assertEquals(
                HomeRenderSource.Online,
                computeHomeRenderSource(OfflineMode.ONLINE, phase, offlineLibrary = library, fallbackPending = false),
            )
        }
    }

    @Test
    fun failedFetch_beforeFirstEmission_isFallbackPending() {
        assertEquals(
            HomeRenderSource.FallbackPending,
            computeHomeRenderSource(OfflineMode.ONLINE, failed(fetchFailed = true), offlineLibrary = emptyList(), fallbackPending = true),
        )
    }

    @Test
    fun failedFetch_withDownloads_isImplicitOffline() {
        assertEquals(
            HomeRenderSource.Offline.Implicit,
            computeHomeRenderSource(OfflineMode.ONLINE, failed(fetchFailed = true), offlineLibrary = library, fallbackPending = false),
        )
    }

    @Test
    fun failedFetch_confirmedEmpty_rendersOnline_hardError() {
        assertEquals(
            HomeRenderSource.Online,
            computeHomeRenderSource(OfflineMode.ONLINE, failed(fetchFailed = true), offlineLibrary = emptyList(), fallbackPending = false),
        )
    }
}
