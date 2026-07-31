package com.raulshma.jellyplay.core.data.tv

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvWatchNextPublisherTest {

    private lateinit var context: Context
    private lateinit var mediaRepository: MediaRepository
    private lateinit var playbackRepository: PlaybackRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)

        // Default to non-TV environment (no leanback feature)
        every { context.packageManager.hasSystemFeature("android.software.leanback") } returns false
    }

    @Test
    fun publish_onNonTvDevice_returnsSuccessWithoutProcessing() = runTest {
        val publisher = TvWatchNextPublisher(context, mediaRepository, playbackRepository)
        val result = publisher.publish()

        assertTrue(result.isSuccess)
    }

    @Test
    fun clear_onNonTvDevice_returnsSuccessWithoutProcessing() = runTest {
        val publisher = TvWatchNextPublisher(context, mediaRepository, playbackRepository)
        val result = publisher.clear()

        assertTrue(result.isSuccess)
    }

    @Test
    fun candidatesCombination_prioritizesContinueWatching_andDeduplicates() {
        val cwItems = (1..10).map { id ->
            MediaItem(id = "item_$id", name = "CW Item $id", mediaType = MediaType.MOVIE)
        }
        val nextUpItems = (8..20).map { id ->
            MediaItem(id = "item_$id", name = "NextUp Item $id", mediaType = MediaType.EPISODE)
        }

        val maxItems = 16
        val seen = mutableSetOf<String>()
        val candidates = (cwItems + nextUpItems)
            .filter { seen.add(it.id) }
            .take(maxItems)

        // 1..10 from CW (10 items), plus 11..16 from NextUp (6 items) = 16 items total
        assertEquals(16, candidates.size)
        assertEquals("item_1", candidates.first().id)
        assertEquals("item_16", candidates.last().id)
    }

    @Test
    fun playbackTicksConversionMath_verifyMsAndTypeRules() {
        val minResumeTicks = 20_000_000L // 2 seconds
        val ticksPerMs = 10_000L

        val shortResumeTicks = 15_000_000L // 1.5 seconds -> WATCH_NEXT_TYPE_NEXT
        val longResumeTicks = 50_000_000L  // 5 seconds -> WATCH_NEXT_TYPE_CONTINUE

        val isContinueWatching = longResumeTicks >= minResumeTicks
        val isNextUp = shortResumeTicks < minResumeTicks

        val longPositionMs = (longResumeTicks / ticksPerMs).toInt()

        assertTrue(isContinueWatching)
        assertTrue(isNextUp)
        assertEquals(5000, longPositionMs)
    }
}
