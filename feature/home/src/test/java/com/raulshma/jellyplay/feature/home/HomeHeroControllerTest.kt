package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun heroController_toggleSurprise_onPicksNewCandidateAndPausesAutoRotate() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = false)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
            ),
        )

        // Turning surprise mode ON: the pick excludes the current index, so with
        // two candidates the featured item must move to the other one.
        controller.toggleSurprise()

        assertTrue(controller.showSurprise)
        assertFalse(controller.autoRotateEnabled)
        assertEquals("m2", controller.featuredItem?.id)
    }

    @Test
    fun heroController_toggleSurprise_offRestoresAutoRotate() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = false)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
            ),
        )

        controller.toggleSurprise()
        // Turning surprise mode OFF (when old showSurprise is true) -> autoRotate should become true
        controller.toggleSurprise()

        assertFalse(controller.showSurprise)
        assertTrue(controller.autoRotateEnabled)
    }

    @Test
    fun heroController_onSurpriseArmed_showsSurpriseAndPausesAutoRotate() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
            ),
        )

        controller.onSurpriseArmed()

        assertTrue(controller.showSurprise)
        assertFalse(controller.autoRotateEnabled)
        assertEquals("m2", controller.featuredItem?.id)
    }

    @Test
    fun heroController_rotationTick_wrapsAroundToFirstCandidate() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
                item("m3", MediaType.MOVIE),
            ),
        )

        controller.rotationTick()
        assertEquals("m2", controller.featuredItem?.id)
        controller.rotationTick()
        assertEquals("m3", controller.featuredItem?.id)
        controller.rotationTick()
        assertEquals("m1", controller.featuredItem?.id)
    }

    @Test
    fun heroController_rotationTick_withEmptyCandidates_isNoOp() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)

        controller.rotationTick()

        assertNull(controller.featuredItem)
        assertNull(controller.backdropUrl)
    }

    @Test
    fun heroController_onFocusChange_updatesFocusState_andSnapStartsUnsettled() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        assertFalse(controller.focusSnapSettled)

        controller.onFocusChange(false)
        assertFalse(controller.focusInHero)

        controller.onFocusChange(true)
        assertTrue(controller.focusInHero)
    }

    @Test
    fun heroController_backdropUrl_delegatesToCtorLambda() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(listOf(item("m1", MediaType.MOVIE)))

        assertEquals("url/m1", controller.backdropUrl)
    }

    @Test
    fun heroController_updateCandidates_preservesStillValidIndex() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
                item("m3", MediaType.MOVIE),
            ),
        )
        controller.rotationTick()
        assertEquals("m2", controller.featuredItem?.id)

        controller.updateCandidates(
            listOf(
                item("n1", MediaType.MOVIE),
                item("n2", MediaType.MOVIE),
                item("n3", MediaType.MOVIE),
            ),
        )
        assertEquals("n2", controller.featuredItem?.id)
    }

    @Test
    fun heroController_updateCandidates_shrunkPoolYieldsNullFeaturedItem() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
                item("m3", MediaType.MOVIE),
            ),
        )
        controller.rotationTick()
        controller.rotationTick()

        // The index is deliberately not clamped: a shrunk pool yields a null
        // featured item until the index is valid again.
        controller.updateCandidates(listOf(item("m1", MediaType.MOVIE)))

        assertNull(controller.featuredItem)
        assertNull(controller.backdropUrl)
    }

    private fun item(id: String, type: MediaType) = MediaItem(
        id = id,
        name = id,
        mediaType = type,
    )
}
