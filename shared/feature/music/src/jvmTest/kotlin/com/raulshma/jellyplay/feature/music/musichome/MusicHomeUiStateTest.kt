package com.raulshma.jellyplay.feature.music.musichome

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.OfflineMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Section-identity integrity: every [MusicHomeSectionType] maps to its OWN
 * display and subtitle resources — a copy-paste swap (two types resolving the
 * same resource) would silently relabel a section, and the exhaustive `when`s
 * would never catch it. Also pins the UI-state defaults (VIDEO home mode,
 * ONLINE, loading with empty sections and no error).
 */
class MusicHomeUiStateTest {

    @Test
    fun sectionType_displayLabels_areUniquePerType() {
        val types = MusicHomeSectionType.entries

        assertEquals(types.size, types.map { it.displayNameRes }.distinct().size)
    }

    @Test
    fun sectionType_subtitleLabels_areUniquePerType() {
        val types = MusicHomeSectionType.entries

        assertEquals(types.size, types.map { it.subtitleRes }.distinct().size)
    }

    @Test
    fun defaultState_isVideoModeOnlineAndLoading() {
        val state = MusicHomeUiState()

        assertEquals(HomeMode.VIDEO, state.homeMode)
        assertEquals(OfflineMode.ONLINE, state.offlineMode)
        assertTrue(state.sections.isEmpty())
        assertTrue(state.isLoading)
        assertNull(state.error)
    }
}
