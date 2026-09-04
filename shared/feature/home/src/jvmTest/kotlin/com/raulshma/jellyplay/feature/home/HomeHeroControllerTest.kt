package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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
    fun selectHomeHeroCandidates_offlineVideoHome_featuresOfflineMoviesAndSeries() {
        val offlineSections = listOf(
            HomeSection(
                id = "offline_recent",
                title = "Recently Downloaded",
                type = HomeSectionType.DOWNLOADED,
                items = listOf(
                    item("d-m1", MediaType.MOVIE),
                    item("d-s1", MediaType.SERIES),
                    item("d-a1", MediaType.AUDIO),
                ),
            ),
        )
        val onlineSections = listOf(section("latest", item("m1", MediaType.MOVIE)))

        val candidates = selectHomeHeroCandidates(
            renderingOffline = true,
            homeMode = HomeMode.VIDEO,
            onlineSections = onlineSections,
            offlineSections = offlineSections,
        )

        // Server titles are never featured offline; audio is skipped.
        assertEquals(listOf("d-m1", "d-s1"), candidates.map { it.id })
    }

    @Test
    fun selectHomeHeroCandidates_offlineMusicHome_isEmpty() {
        val candidates = selectHomeHeroCandidates(
            renderingOffline = true,
            homeMode = HomeMode.MUSIC,
            onlineSections = listOf(section("latest", item("m1", MediaType.MOVIE))),
            offlineSections = listOf(
                HomeSection(
                    id = "offline_music",
                    title = "Music",
                    type = HomeSectionType.DOWNLOADED,
                    items = listOf(item("alb1", MediaType.ALBUM)),
                ),
            ),
        )

        // The online music home never renders a hero; neither does the offline one.
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun selectHomeHeroCandidates_online_ignoresOfflineSections() {
        val candidates = selectHomeHeroCandidates(
            renderingOffline = false,
            homeMode = HomeMode.VIDEO,
            onlineSections = listOf(section("latest", item("m1", MediaType.MOVIE))),
            offlineSections = listOf(
                HomeSection(
                    id = "offline_movies",
                    title = "Movies",
                    type = HomeSectionType.DOWNLOADED,
                    items = listOf(item("d-m1", MediaType.MOVIE)),
                ),
            ),
        )

        assertEquals(listOf("m1"), candidates.map { it.id })
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

    // --- rotation cadence policy (rotationDelayMs / shouldTickNow) ---

    @Test
    fun heroController_rotationDelayMs_emptyCandidates_returnsNull() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)

        // All four gates precede any timing, for both scroll states.
        assertNull(controller.rotationDelayMs(isScrolling = false, lifecycleResumed = true))
        assertNull(controller.rotationDelayMs(isScrolling = true, lifecycleResumed = true))
    }

    @Test
    fun heroController_rotationDelayMs_autoRotateDisabled_returnsNull() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = false)
        controller.updateCandidates(listOf(item("m1", MediaType.MOVIE)))

        assertNull(controller.rotationDelayMs(isScrolling = false, lifecycleResumed = true))
        assertNull(controller.rotationDelayMs(isScrolling = true, lifecycleResumed = true))
    }

    @Test
    fun heroController_rotationDelayMs_focusOutsideHero_returnsNull() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(listOf(item("m1", MediaType.MOVIE)))

        controller.onFocusChange(false)

        assertNull(controller.rotationDelayMs(isScrolling = false, lifecycleResumed = true))
        assertNull(controller.rotationDelayMs(isScrolling = true, lifecycleResumed = true))
    }

    @Test
    fun heroController_rotationDelayMs_lifecycleNotResumed_returnsNull() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(listOf(item("m1", MediaType.MOVIE)))

        assertNull(controller.rotationDelayMs(isScrolling = false, lifecycleResumed = false))
        assertNull(controller.rotationDelayMs(isScrolling = true, lifecycleResumed = false))
    }

    @Test
    fun heroController_rotationDelayMs_scrollingDefersRecheck_idleTicks() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
            ),
        )

        // The cadence values are pinned: 2s defer while scrolling, 8s tick when idle.
        assertEquals(2000L, controller.rotationDelayMs(isScrolling = true, lifecycleResumed = true))
        assertEquals(8000L, controller.rotationDelayMs(isScrolling = false, lifecycleResumed = true))
    }

    @Test
    fun heroController_shouldTickNow_falseAfterAutoRotateFlipsOffMidDelay() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
            ),
        )
        assertTrue(controller.shouldTickNow())

        // Mid-delay flip: the user taps "Surprise Me", which disables rotation.
        controller.toggleSurprise()

        assertFalse(controller.shouldTickNow())
        assertNull(controller.rotationDelayMs(isScrolling = false, lifecycleResumed = true))
    }

    @Test
    fun heroController_shouldTickNow_falseWhenFocusLeavesHero() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        controller.updateCandidates(
            listOf(
                item("m1", MediaType.MOVIE),
                item("m2", MediaType.MOVIE),
            ),
        )

        controller.onFocusChange(false)

        assertFalse(controller.shouldTickNow())
    }

    // --- TV snap-to-top policy (onFocusEffect) ---

    @Test
    fun heroController_onFocusEffect_firstInvocationConsumesSkipRegardlessOfArgs() {
        for ((focused, isTv) in listOf(true to true, true to false, false to true, false to false)) {
            val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)

            // The first emission of the focus effect only settles the skip,
            // never snaps — whatever the focus/TV state.
            assertFalse(controller.onFocusEffect(focused = focused, isTv = isTv))
            assertTrue(controller.focusSnapSettled)
        }
    }

    @Test
    fun heroController_onFocusEffect_snapsOnlyWhenFocusedOnTv() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)

        assertFalse(controller.onFocusEffect(focused = true, isTv = true)) // skip consumed
        assertTrue(controller.onFocusEffect(focused = true, isTv = true))
        assertFalse(controller.onFocusEffect(focused = false, isTv = true))
        assertFalse(controller.onFocusEffect(focused = true, isTv = false))
        // The settle flag persists, so a later focused+TV call still snaps.
        assertTrue(controller.onFocusEffect(focused = true, isTv = true))
    }

    // --- composition-write pin: repeated identical updateCandidates calls ---

    @Test
    fun heroController_updateCandidates_repeatedIdenticalCallsPreserveIndex() {
        val controller = HeroController(getBackdropUrl = { "url/$it" }, initialAutoRotateEnabled = true)
        val candidates = listOf(
            item("m1", MediaType.MOVIE),
            item("m2", MediaType.MOVIE),
            item("m3", MediaType.MOVIE),
        )
        controller.updateCandidates(candidates)
        controller.rotationTick()
        assertEquals("m2", controller.featuredItem?.id)

        // The composition-write re-runs whenever the remember key re-fires;
        // identical content must never reset the rotation index.
        controller.updateCandidates(candidates)
        controller.updateCandidates(candidates.map { it })
        assertEquals("m2", controller.featuredItem?.id)
        assertEquals("url/m2", controller.backdropUrl)
    }

    private fun item(id: String, type: MediaType) = MediaItem(
        id = id,
        name = id,
        mediaType = type,
    )

    private fun section(id: String, vararg items: MediaItem) = HomeSection(
        id = id,
        title = id,
        type = HomeSectionType.LATEST_MEDIA,
        items = items.toList(),
    )
}
