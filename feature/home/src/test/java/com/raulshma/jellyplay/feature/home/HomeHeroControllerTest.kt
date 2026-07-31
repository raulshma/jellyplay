package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeHeroControllerTest {

    @Test
    fun selectFeaturedCandidates_emptySections_returnsEmptyList() {
        val candidates = selectFeaturedCandidates(emptyList())
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun selectFeaturedCandidates_prefersLatestMediaMoviesAndSeriesUpToThree() {
        val latestSection = HomeSection(
            id = "latest",
            title = "Latest",
            type = HomeSectionType.LATEST_MEDIA,
            items = listOf(
                item("m1", MediaType.MOVIE),
                item("s1", MediaType.SERIES),
                item("a1", MediaType.AUDIO),
                item("m2", MediaType.MOVIE),
                item("m3", MediaType.MOVIE),
            ),
        )
        val candidates = selectFeaturedCandidates(listOf(latestSection))
        assertEquals(listOf("m1", "s1", "m2"), candidates.map { it.id })
    }

    @Test
    fun selectFeaturedCandidates_fallsBackToAllMoviesAndSeries_whenLatestMediaHasNone() {
        val cwSection = HomeSection(
            id = "cw",
            title = "Continue Watching",
            type = HomeSectionType.CONTINUE_WATCHING,
            items = listOf(
                item("cw1", MediaType.MOVIE),
                item("cw2", MediaType.SERIES),
            ),
        )
        val candidates = selectFeaturedCandidates(listOf(cwSection))
        assertEquals(listOf("cw1", "cw2"), candidates.map { it.id })
    }

    @Test
    fun selectFeaturedCandidates_fallsBackToAllItems_whenNoMoviesOrSeriesExist() {
        val audioSection = HomeSection(
            id = "audio",
            title = "Audio",
            type = HomeSectionType.RECENTLY_ADDED,
            items = listOf(
                item("aud1", MediaType.AUDIO),
                item("photo1", MediaType.PHOTO),
            ),
        )
        val candidates = selectFeaturedCandidates(listOf(audioSection))
        assertEquals(listOf("aud1", "photo1"), candidates.map { it.id })
    }

    @Test
    fun heroController_toggleSurprise_togglesStateAndRestoresAutoRotate() {
        var surprise = false
        var autoRotate = false
        val controller = HeroController(
            featuredItem = item("m1", MediaType.MOVIE),
            backdropUrl = "http://example.com/bg.jpg",
            showSurprise = surprise,
            setShowSurprise = { surprise = it },
            autoRotateEnabled = autoRotate,
            setAutoRotateEnabled = { autoRotate = it },
            focusInHero = true,
            setFocusInHero = {},
        )

        // Turning surprise mode ON (when old showSurprise is false)
        controller.toggleSurprise()
        assertTrue(surprise)

        // Turning surprise mode OFF (when old showSurprise is true) -> autoRotate should become true
        val controllerOff = HeroController(
            featuredItem = item("m1", MediaType.MOVIE),
            backdropUrl = "http://example.com/bg.jpg",
            showSurprise = surprise, // true now
            setShowSurprise = { surprise = it },
            autoRotateEnabled = autoRotate,
            setAutoRotateEnabled = { autoRotate = it },
            focusInHero = true,
            setFocusInHero = {},
        )
        controllerOff.toggleSurprise()
        assertFalse(surprise)
        assertTrue(autoRotate)
    }

    @Test
    fun heroController_onFocusChange_updatesFocusState() {
        var focusedState = false
        val controller = HeroController(
            featuredItem = null,
            backdropUrl = null,
            showSurprise = false,
            setShowSurprise = {},
            autoRotateEnabled = true,
            setAutoRotateEnabled = {},
            focusInHero = focusedState,
            setFocusInHero = { focusedState = it },
        )

        controller.onFocusChange(true)
        assertTrue(focusedState)

        controller.onFocusChange(false)
        assertFalse(focusedState)
    }

    private fun item(id: String, type: MediaType) = MediaItem(
        id = id,
        name = id,
        mediaType = type,
    )
}
