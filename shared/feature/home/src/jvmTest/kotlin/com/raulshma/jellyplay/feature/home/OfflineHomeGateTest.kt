package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Drives [OfflineHomeGate] through its interface — the gate semantics that
 * were previously pinnable only by constructing the whole 33-collaborator VM
 * under Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineHomeGateTest {

    private val offlineMode = MutableStateFlow(OfflineMode.ONLINE)
    private val fetchFailed = MutableStateFlow(false)
    private val libraryFlow = MutableSharedFlow<List<OfflineMediaItem>>(extraBufferCapacity = 8)

    private val offlineRepository: OfflineRepository = mockk<OfflineRepository>(relaxed = true).apply {
        every { getOfflineLibrary() } returns libraryFlow
        every { getOfflineEpisodes() } returns flowOf(emptyList())
    }

    private fun downloads(vararg ids: String) = ids.map {
        OfflineMediaItem(id = it, name = it, mediaType = MediaType.MOVIE)
    }

    @Test
    fun gateClosed_whileOnlineWithContent_neverCollectsRepository() = runTest {
        val gate = OfflineHomeGate(
            scope = backgroundScope,
            offlineMode = offlineMode,
            offlineRepository = offlineRepository,
            fetchFailed = fetchFailed,
        )
        runCurrent()

        // The gate stays closed while online with nothing failed: no
        // collection, so download-progress writes can't re-invalidate the tree.
        assertEquals(HomeRenderSource.Online, gate.state.value.renderSource)
        verify(exactly = 0) { offlineRepository.getOfflineLibrary() }
        verify(exactly = 0) { offlineRepository.getOfflineEpisodes() }
    }

    @Test
    fun failedFetch_opensGateAndRendersFallbackPendingThenImplicitOffline() = runTest {
        val gate = OfflineHomeGate(
            scope = backgroundScope,
            offlineMode = offlineMode,
            offlineRepository = offlineRepository,
            fetchFailed = fetchFailed,
        )
        runCurrent()

        // Failed fetch opens the gate (implicit offline) — even when stale online sections are on screen.
        fetchFailed.value = true
        runCurrent()

        // Pre-emission window: pending render source, not the hard error.
        assertEquals(HomeRenderSource.FallbackPending, gate.state.value.renderSource)

        // First library emission with downloads present: implicit-offline.
        libraryFlow.tryEmit(downloads("d1"))
        runCurrent()

        assertEquals(HomeRenderSource.Offline.Implicit, gate.state.value.renderSource)
        assertEquals(listOf("d1"), gate.state.value.offlineLibrary.map { it.id })
    }

    @Test
    fun failedFetch_overConfirmedEmptyLibrary_staysOnline() = runTest {
        val gate = OfflineHomeGate(
            scope = backgroundScope,
            offlineMode = offlineMode,
            offlineRepository = offlineRepository,
            fetchFailed = fetchFailed,
        )
        runCurrent()

        fetchFailed.value = true
        runCurrent()
        libraryFlow.tryEmit(emptyList())
        runCurrent()

        // Downloads confirmed absent: back to Online (the hard-error screen
        // owns that corner) — the known-empty rule of computeHomeRenderSource.
        assertEquals(HomeRenderSource.Online, gate.state.value.renderSource)
    }

    @Test
    fun manualOfflineMode_rendersExplicitOffline() = runTest {
        val gate = OfflineHomeGate(
            scope = backgroundScope,
            offlineMode = offlineMode,
            offlineRepository = offlineRepository,
            fetchFailed = fetchFailed,
        )
        runCurrent()

        offlineMode.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        libraryFlow.tryEmit(downloads("d1"))
        runCurrent()

        assertEquals(HomeRenderSource.Offline.Explicit, gate.state.value.renderSource)
    }

    @Test
    fun gateClosing_dropsTheCollectedLibrary() = runTest {
        val gate = OfflineHomeGate(
            scope = backgroundScope,
            offlineMode = offlineMode,
            offlineRepository = offlineRepository,
            fetchFailed = fetchFailed,
        )
        runCurrent()

        offlineMode.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()
        libraryFlow.tryEmit(downloads("d1"))
        runCurrent()
        assertEquals(listOf("d1"), gate.state.value.offlineLibrary.map { it.id })

        // Back online with a good fetch: the gate closes and the offline
        // slice resets to empty (the online home never renders stale rows).
        offlineMode.value = OfflineMode.ONLINE
        fetchFailed.value = false
        runCurrent()

        assertEquals(HomeRenderSource.Online, gate.state.value.renderSource)
        assertEquals(emptyList<String>(), gate.state.value.offlineLibrary.map { it.id })
    }

    @Test
    fun episodes_arriveIndependentlyOfTheLibraryFold() = runTest {
        val episodes = listOf(OfflineMediaItem(id = "e1", name = "Ep", mediaType = MediaType.EPISODE))
        every { offlineRepository.getOfflineEpisodes() } returns flowOf(episodes)
        val gate = OfflineHomeGate(
            scope = backgroundScope,
            offlineMode = offlineMode,
            offlineRepository = offlineRepository,
            fetchFailed = fetchFailed,
        )
        runCurrent()

        offlineMode.value = OfflineMode.OFFLINE_MANUAL
        runCurrent()

        // Episodes landed even though the library flow has not emitted yet —
        // their collector must not wait on the library's pending→loaded
        // transition. (Explicit offline renders immediately per
        // computeHomeRenderSource's priority; no library emission needed.)
        assertEquals(episodes, gate.state.value.offlineEpisodes)
        assertEquals(HomeRenderSource.Offline.Explicit, gate.state.value.renderSource)
    }
}
