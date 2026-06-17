package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.OfflineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOfflineModeLogicTest {

    // ─── OfflineMode enum ─────────────────────────────────────────────────────

    @Test
    fun offlineMode_hasThreeValues() {
        assertEquals(3, OfflineMode.entries.size)
    }

    @Test
    fun offlineMode_onlineValue_exists() {
        assertEquals(OfflineMode.ONLINE, OfflineMode.valueOf("ONLINE"))
    }

    @Test
    fun offlineMode_offlineManualValue_exists() {
        assertEquals(OfflineMode.OFFLINE_MANUAL, OfflineMode.valueOf("OFFLINE_MANUAL"))
    }

    @Test
    fun offlineMode_offlineAutoValue_exists() {
        assertEquals(OfflineMode.OFFLINE_AUTO, OfflineMode.valueOf("OFFLINE_AUTO"))
    }

    @Test
    fun offlineMode_isOffline_whenManual() {
        val mode = OfflineMode.OFFLINE_MANUAL
        val isOffline = mode != OfflineMode.ONLINE
        assertTrue(isOffline)
    }

    @Test
    fun offlineMode_isOffline_whenAuto() {
        val mode = OfflineMode.OFFLINE_AUTO
        val isOffline = mode != OfflineMode.ONLINE
        assertTrue(isOffline)
    }

    @Test
    fun offlineMode_isNotOffline_whenOnline() {
        val mode = OfflineMode.ONLINE
        val isOffline = mode != OfflineMode.ONLINE
        assertFalse(isOffline)
    }

    // ─── State machine: sections cleared when going offline ───────────────────

    @Test
    fun offlineTransition_onlineToOffline_clearsSections() {
        var state = HomeUiState(
            sections = listOf(com.raulshma.jellyplay.core.model.HomeSection(
                id = "cw2",
                type = com.raulshma.jellyplay.core.model.HomeSectionType.CONTINUE_WATCHING,
                items = emptyList(),
                title = "Continue Watching",
            )),
            offlineMode = OfflineMode.ONLINE,
        )

        // Simulating the offline transition logic:
        // if (mode != ONLINE && !wasOffline) → clear sections
        val wasOffline = state.offlineMode != OfflineMode.ONLINE
        val newMode = OfflineMode.OFFLINE_MANUAL
        state = if (newMode != OfflineMode.ONLINE && !wasOffline) {
            state.copy(offlineMode = newMode, sections = emptyList())
        } else {
            state.copy(offlineMode = newMode)
        }

        assertEquals(OfflineMode.OFFLINE_MANUAL, state.offlineMode)
        assertTrue(state.sections.isEmpty())
    }

    @Test
    fun offlineTransition_offlineToOnline_doesNotAutoClearSections() {
        val section = com.raulshma.jellyplay.core.model.HomeSection(
            id = "cw3",
            type = com.raulshma.jellyplay.core.model.HomeSectionType.CONTINUE_WATCHING,
            items = emptyList(),
            title = "Continue Watching",
        )
        var state = HomeUiState(
            sections = listOf(section),
            offlineMode = OfflineMode.OFFLINE_MANUAL,
        )

        // Simulating: if (mode == ONLINE && wasOffline) → trigger refresh (not immediate clear)
        val wasOffline = state.offlineMode != OfflineMode.ONLINE
        val newMode = OfflineMode.ONLINE
        state = state.copy(offlineMode = newMode)

        assertEquals(OfflineMode.ONLINE, state.offlineMode)
        // Sections are NOT cleared immediately; a new fetch is triggered
        assertFalse(state.sections.isEmpty())
        assertTrue(wasOffline) // confirm the transition occurred
    }

    @Test
    fun offlineMode_autoToManual_isStillOffline() {
        val mode = OfflineMode.OFFLINE_AUTO
        val newMode = OfflineMode.OFFLINE_MANUAL
        assertNotEquals(OfflineMode.ONLINE, mode)
        assertNotEquals(OfflineMode.ONLINE, newMode)
    }

    // ─── State transitions ─────────────────────────────────────────────────────

    @Test
    fun uiState_offlineModeUpdate_onlyChangesOfflineField() {
        val original = HomeUiState(isLoading = false, error = "prior error")
        val updated = original.copy(offlineMode = OfflineMode.OFFLINE_AUTO)
        assertEquals(OfflineMode.OFFLINE_AUTO, updated.offlineMode)
        // Other fields preserved
        assertFalse(updated.isLoading)
        assertEquals("prior error", updated.error)
    }

    @Test
    fun uiState_offlineLibrarySet_preservesOnlineState() {
        val items = listOf(
            com.raulshma.jellyplay.core.model.OfflineMediaItem(
                id = "1", name = "Movie", mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE,
                downloadPath = "/downloads/movie.mkv",
            )
        )
        val state = HomeUiState().copy(offlineLibrary = items)
        assertEquals(1, state.offlineLibrary.size)
        assertEquals(OfflineMode.ONLINE, state.offlineMode)
    }
}
