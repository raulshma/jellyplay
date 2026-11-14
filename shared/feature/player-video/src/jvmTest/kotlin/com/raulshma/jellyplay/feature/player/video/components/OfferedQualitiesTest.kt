package com.raulshma.jellyplay.feature.player.video.components

import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the quality-tier offer rule (the F-Droid review's 4K-on-1080p report):
 * transcoding never upscales, so a tier above the source does not render as a
 * resolution tier. One exception keeps playback options complete: 4K resolves
 * to *no* bitrate cap, so below-4K sources keep it as "Original (uncapped)"
 * ([offersOriginalUncapped]) and metered playback never loses the one-tap
 * uncapped choice. Quality classes are width-conventional though, so the cap
 * is judged against the 16:9 height the source's width implies too — a scope
 * master (1920x800) still counts as 1080p. Unknown source dimensions keep
 * every tier; AUTO always stays.
 */
class OfferedQualitiesTest {

    @Test
    fun unknownDimensionsShowPlainTiers() {
        assertTrue(!offersOriginalUncapped(null, null))
    }

    @Test
    fun below4kSourcePresentsOriginalUncapped() {
        assertTrue(offersOriginalUncapped(null, 1080))
        assertTrue(offersOriginalUncapped(1920, 800))
        assertTrue(offersOriginalUncapped(1920, null))
        assertTrue(offersOriginalUncapped(null, 360))
    }

    @Test
    fun sourceAt4kShowsPlainTier() {
        assertTrue(!offersOriginalUncapped(null, 2160))
        assertTrue(!offersOriginalUncapped(3840, 1600))
        assertTrue(!offersOriginalUncapped(null, 3000))
    }


    @Test
    fun unknownSourceOffersEveryTier() {
        assertEquals(StreamingQuality.entries, offeredQualities(null, null))
    }

    @Test
    fun source1080pDrops4k() {
        val offered = offeredQualities(null, 1080)
        assertEquals(
            listOf(
                StreamingQuality.AUTO,
                StreamingQuality.LOW_360P,
                StreamingQuality.SD_480P,
                StreamingQuality.HD_720P,
                StreamingQuality.FHD_1080P,
            ),
            offered,
        )
    }

    @Test
    fun source4kOffersEveryTier() {
        assertEquals(StreamingQuality.entries, offeredQualities(null, 2160))
    }

    @Test
    fun source720pCapsAt720() {
        assertEquals(
            listOf(
                StreamingQuality.AUTO,
                StreamingQuality.LOW_360P,
                StreamingQuality.SD_480P,
                StreamingQuality.HD_720P,
            ),
            offeredQualities(null, 720),
        )
    }

    @Test
    fun source360pKeepsOnlyAutoAnd360() {
        assertEquals(
            listOf(StreamingQuality.AUTO, StreamingQuality.LOW_360P),
            offeredQualities(null, 360),
        )
    }

    @Test
    fun nonStandardHeightKeepsTiersAtOrBelow() {
        val offered = offeredQualities(null, 900)
        assertTrue(StreamingQuality.HD_720P in offered)
        assertTrue(StreamingQuality.FHD_1080P !in offered)
        assertTrue(StreamingQuality.UHD_4K !in offered)
    }

    @Test
    fun autoAlwaysOfferedEvenForTinySources() {
        assertEquals(listOf(StreamingQuality.AUTO), offeredQualities(null, 144))
    }

    /** The review follow-up: "1080p" = 1920 across, so a scope master keeps the tier. */
    @Test
    fun scope1080pMasterKeeps1080pTier() {
        val offered = offeredQualities(1920, 800)
        assertTrue(StreamingQuality.FHD_1080P in offered)
        assertTrue(StreamingQuality.UHD_4K !in offered)
        assertEquals(
            listOf(
                StreamingQuality.AUTO,
                StreamingQuality.LOW_360P,
                StreamingQuality.SD_480P,
                StreamingQuality.HD_720P,
                StreamingQuality.FHD_1080P,
            ),
            offered,
        )
    }

    @Test
    fun scope4kMasterKeeps4kTier() {
        assertEquals(StreamingQuality.entries, offeredQualities(3840, 1600))
    }

    @Test
    fun scope720pMasterCapsAt720p() {
        val offered = offeredQualities(1280, 536)
        assertTrue(StreamingQuality.HD_720P in offered)
        assertTrue(StreamingQuality.FHD_1080P !in offered)
        assertTrue(StreamingQuality.UHD_4K !in offered)
    }

    /** A 4:3 frame is judged by its coded height — 1440-wide implies only 810. */
    @Test
    fun fourByThreeSourceJudgedByCodedHeight() {
        val offered = offeredQualities(1440, 1080)
        assertTrue(StreamingQuality.FHD_1080P in offered)
        assertTrue(StreamingQuality.UHD_4K !in offered)
    }

    @Test
    fun widthOnlyStillClassesTiers() {
        val offered = offeredQualities(1920, null)
        assertTrue(StreamingQuality.FHD_1080P in offered)
        assertTrue(StreamingQuality.UHD_4K !in offered)
    }

    @Test
    fun heightOnlyKeepsHeightRule() {
        val offered = offeredQualities(null, 800)
        assertTrue(StreamingQuality.HD_720P in offered)
        assertTrue(StreamingQuality.FHD_1080P !in offered)
    }
}
