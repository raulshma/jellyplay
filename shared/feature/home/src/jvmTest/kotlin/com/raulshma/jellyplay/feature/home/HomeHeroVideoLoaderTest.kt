package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Hero video: the TTL-memoised two-tier clip probe — the ThemeVideo leg
 * first, the trailer-embed leg (detail TMDB id → Seerr videos → a YouTube
 * key) only on a theme miss and only for movie/series candidates. One probe
 * chain per candidate per window (rotation revisit and pause-chip re-mount
 * serve the memo), misses and failures memoized identically so a clipless
 * item or a down endpoint degrades to the static hero without re-hammering
 * the network, the stream URL always re-shaped fresh (the memo holds metadata
 * only — a stale session token can never be served), and the bounded-entry
 * prune.
 */
class HomeHeroVideoLoaderTest {

    private val libraryApiClient: LibraryApiClient = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val detailFeed: DetailFeed = mockk(relaxed = true)
    private val seerrRepository: SeerrRepository = mockk(relaxed = true)
    private var nowMs = 1_000_000L

    private fun buildLoader(ttlMs: Long = HomeHeroVideoLoader.DEFAULT_TTL_MS) = HomeHeroVideoLoader(
        libraryApiClient = libraryApiClient,
        playbackRepository = playbackRepository,
        detailFeed = detailFeed,
        seerrRepository = seerrRepository,
        clock = { nowMs },
        ttlMs = ttlMs,
    )

    private fun stubClip(itemId: String, clipId: String?) {
        coEvery { libraryApiClient.getThemeVideos(itemId) } returns
            Result.success(clipId?.let { listOf(item(it)) } ?: emptyList())
        if (clipId != null) {
            // getStreamUrl is the non-suspend string shaper — `every`, not `coEvery`.
            every {
                playbackRepository.getStreamUrl(itemId = clipId, mediaSourceId = clipId)
            } returns "https://stream/$clipId"
        }
    }

    private fun stubTrailerKey(itemId: String, mediaType: MediaType, tmdbId: Int?, key: String?) {
        coEvery { detailFeed.getMediaDetail(itemId, force = false) } returns
            Result.success(MediaDetail(item = item(itemId), providerIds = tmdbId?.let { mapOf("tmdb" to it.toString()) } ?: emptyMap()))
        if (tmdbId != null && key != null) {
            coEvery { seerrRepository.getTmdbVideos(tmdbId, mediaType) } returns
                Result.success(listOf(SeerrRelatedVideo(key = key, site = "YouTube")))
        }
    }

    @Test
    fun themeVideoHit_skipsTheEmbedLeg() = runTest {
        stubClip("m1", "clip1")
        val loader = buildLoader()

        val clip = loader.heroClip("m1", MediaType.MOVIE)

        assertEquals(HomeHeroVideoLoader.HeroClip.ThemeVideo("https://stream/clip1"), clip)
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), force = false) }
    }

    @Test
    fun themeVideoMiss_fallsBackToTheTrailerEmbed() = runTest {
        stubClip("m1", clipId = null)
        stubTrailerKey("m1", MediaType.MOVIE, tmdbId = 123, key = "abc")
        val loader = buildLoader()

        assertEquals(HomeHeroVideoLoader.HeroClip.TrailerEmbed("abc"), loader.heroClip("m1", MediaType.MOVIE))
        coVerify(exactly = 1) { seerrRepository.getTmdbVideos(123, MediaType.MOVIE) }
    }

    @Test
    fun embedLeg_skipsNonMovieSeriesCandidates() = runTest {
        stubClip("e1", clipId = null)
        val loader = buildLoader()

        assertNull(loader.heroClip("e1", MediaType.EPISODE))
        coVerify(exactly = 0) { detailFeed.getMediaDetail(any(), force = false) }
    }

    @Test
    fun nonYouTubeVideos_neverYieldAnEmbedKey() = runTest {
        stubClip("m1", clipId = null)
        coEvery { detailFeed.getMediaDetail("m1", force = false) } returns
            Result.success(MediaDetail(item = item("m1"), providerIds = mapOf("tmdb" to "123")))
        coEvery { seerrRepository.getTmdbVideos(123, MediaType.MOVIE) } returns
            Result.success(listOf(SeerrRelatedVideo(key = "v1", site = "Vimeo")))
        val loader = buildLoader()

        assertNull(loader.heroClip("m1", MediaType.MOVIE))
    }

    @Test
    fun revisitWithinTtl_servesTheMemo_withoutReprobing() = runTest {
        stubClip("m1", "clip1")
        val loader = buildLoader()
        loader.heroClip("m1", MediaType.MOVIE)

        // Well inside the window: a hero rotation back to the candidate (or a
        // pause-chip re-mount) must not re-hit the network…
        nowMs += HomeHeroVideoLoader.DEFAULT_TTL_MS - 1
        assertEquals(HomeHeroVideoLoader.HeroClip.ThemeVideo("https://stream/clip1"), loader.heroClip("m1", MediaType.MOVIE))
        coVerify(exactly = 1) { libraryApiClient.getThemeVideos("m1") }
        // …and the URL is re-shaped (the memo holds the clip id only).
        verify(exactly = 2) { playbackRepository.getStreamUrl(itemId = "clip1", mediaSourceId = "clip1") }
    }

    @Test
    fun probePastTtl_refetches() = runTest {
        stubClip("m1", "clip1")
        val loader = buildLoader()
        loader.heroClip("m1", MediaType.MOVIE)

        nowMs += HomeHeroVideoLoader.DEFAULT_TTL_MS
        assertEquals(HomeHeroVideoLoader.HeroClip.ThemeVideo("https://stream/clip1"), loader.heroClip("m1", MediaType.MOVIE))
        coVerify(exactly = 2) { libraryApiClient.getThemeVideos("m1") }
    }

    @Test
    fun cliplessItem_degradesToNull_andMemoizesTheMiss() = runTest {
        stubClip("m1", clipId = null)
        stubTrailerKey("m1", MediaType.MOVIE, tmdbId = null, key = null)
        val loader = buildLoader()

        assertNull(loader.heroClip("m1", MediaType.MOVIE))

        // The miss memoizes for the same window — no reprobe on the revisit.
        nowMs += HomeHeroVideoLoader.DEFAULT_TTL_MS - 1
        assertNull(loader.heroClip("m1", MediaType.MOVIE))
        coVerify(exactly = 1) { libraryApiClient.getThemeVideos("m1") }
        coVerify(exactly = 1) { detailFeed.getMediaDetail("m1", force = false) }
    }

    @Test
    fun failedThemeLeg_stillTriesTheEmbedLeg_andMemoizesBoth() = runTest {
        coEvery { libraryApiClient.getThemeVideos("m1") } returns Result.failure(IllegalStateException("down"))
        stubTrailerKey("m1", MediaType.MOVIE, tmdbId = 123, key = "abc")
        val loader = buildLoader()

        assertEquals(HomeHeroVideoLoader.HeroClip.TrailerEmbed("abc"), loader.heroClip("m1", MediaType.MOVIE))

        nowMs += HomeHeroVideoLoader.DEFAULT_TTL_MS - 1
        assertEquals(HomeHeroVideoLoader.HeroClip.TrailerEmbed("abc"), loader.heroClip("m1", MediaType.MOVIE))
        coVerify(exactly = 1) { seerrRepository.getTmdbVideos(123, MediaType.MOVIE) }
    }

    @Test
    fun blankStreamUrl_degradesToNull_withoutInvalidatingTheClipMemo() = runTest {
        stubClip("m1", "clip1")
        every { playbackRepository.getStreamUrl(itemId = "clip1", mediaSourceId = "clip1") } returns ""
        val loader = buildLoader()

        assertNull(loader.heroClip("m1", MediaType.MOVIE))

        // The clip metadata is still fresh — only the URL shaping repeats.
        // The memo is authoritative (the embed leg is decided at probe time,
        // not re-opened by a session-level URL failure), so the degrade is to
        // the static hero, not the embed.
        nowMs += HomeHeroVideoLoader.DEFAULT_TTL_MS - 1
        assertNull(loader.heroClip("m1", MediaType.MOVIE))
        coVerify(exactly = 1) { libraryApiClient.getThemeVideos("m1") }
    }

    @Test
    fun memoIsBounded_stalestEntriesEvicted() = runTest {
        val loader = buildLoader()
        repeat(HomeHeroVideoLoaderTest.MEMO_CAP + 1) { index ->
            stubClip("m$index", "clip$index")
            nowMs += 1
            loader.heroClip("m$index", MediaType.MOVIE)
        }

        // The FIRST candidate's entry was the stalest — past the cap it was
        // evicted, so a revisit refetches it.
        loader.heroClip("m0", MediaType.MOVIE)
        coVerify(exactly = 2) { libraryApiClient.getThemeVideos("m0") }
        // A middle candidate survives.
        loader.heroClip("m5", MediaType.MOVIE)
        coVerify(exactly = 1) { libraryApiClient.getThemeVideos("m5") }
    }

    @Test
    fun heroClip_variantsAreExhaustive() {
        // A compile-time-shaped pin: the two transports stay the only kinds
        // (the Crossfade mount's `when` relies on exhaustiveness).
        val clips = listOf(
            HomeHeroVideoLoader.HeroClip.ThemeVideo("u"),
            HomeHeroVideoLoader.HeroClip.TrailerEmbed("k"),
        )
        assertTrue(clips.all { it is HomeHeroVideoLoader.HeroClip })
    }

    private fun item(id: String) = MediaItem(
        id = id,
        name = id,
        mediaType = MediaType.MOVIE,
    )

    private companion object {
        /** Mirrors the loader's private cap — the pin catches an accidental unbound memo. */
        const val MEMO_CAP = 16
    }
}
