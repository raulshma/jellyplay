package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for CinemaIntroUiState data class. */
class CinemaIntroUiStateTest {

    @Test
    fun cinemaIntroState_holdsAllFields() {
        val state = CinemaIntroUiState(
            title = "Funimation Intro",
            currentIndex = 1,
            totalCount = 3,
        )
        assertEquals("Funimation Intro", state.title)
        assertEquals(1, state.currentIndex)
        assertEquals(3, state.totalCount)
    }

    @Test
    fun cinemaIntroState_firstItem_indexZero() {
        val state = CinemaIntroUiState(title = "Intro", currentIndex = 0, totalCount = 2)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun cinemaIntroState_singleItem_totalCountOne() {
        val state = CinemaIntroUiState(title = "Bumper", currentIndex = 0, totalCount = 1)
        assertEquals(1, state.totalCount)
    }

    @Test
    fun cinemaIntroState_dataClass_equality() {
        val a = CinemaIntroUiState("Intro", 1, 3)
        val b = CinemaIntroUiState("Intro", 1, 3)
        assertEquals(a, b)
    }

    @Test
    fun cinemaIntroState_dataClass_inequality_differentTitle() {
        val a = CinemaIntroUiState("Intro A", 1, 3)
        val b = CinemaIntroUiState("Intro B", 1, 3)
        assert(a != b)
    }

    @Test
    fun cinemaIntroState_copy_updatesTitle() {
        val state = CinemaIntroUiState("Old Title", 0, 1)
        val updated = state.copy(title = "New Title")
        assertEquals("New Title", updated.title)
    }

    @Test
    fun cinemaIntroState_copy_updatesCurrentIndex() {
        val state = CinemaIntroUiState("Title", 0, 3)
        val updated = state.copy(currentIndex = 2)
        assertEquals(2, updated.currentIndex)
    }

    @Test
    fun videoPlayerUiState_cinemaIntroState_defaultIsNull() {
        val state = VideoPlayerUiState()
        assertNull(state.cinemaIntroState)
    }

    @Test
    fun videoPlayerUiState_cinemaIntroState_canBeSet() {
        val intro = CinemaIntroUiState(title = "Crunchyroll Bumper", currentIndex = 0, totalCount = 1)
        val state = VideoPlayerUiState(cinemaIntroState = intro)
        assertNotNull(state.cinemaIntroState)
        assertEquals("Crunchyroll Bumper", state.cinemaIntroState!!.title)
    }

    @Test
    fun videoPlayerUiState_cinemaIntroState_canBeCleared() {
        val intro = CinemaIntroUiState("Bumper", 0, 1)
        val state = VideoPlayerUiState(cinemaIntroState = intro).copy(cinemaIntroState = null)
        assertNull(state.cinemaIntroState)
    }
}
