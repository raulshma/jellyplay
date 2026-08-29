package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.progressFraction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts the PRODUCTION [MediaItem.progressFraction] — the card rows read
 * progress exclusively through it; previously these tests re-declared the
 * division inline and asserted against that copy.
 */
class HomeMediaRowsTest {

    @Test
    fun progressFraction_halfWatched_isHalf() {
        val item = MediaItem(
            id = "i1",
            name = "Item",
            mediaType = MediaType.MOVIE,
            runTimeTicks = 600_000_000L,
            playbackPositionTicks = 300_000_000L,
        )

        assertEquals(0.5f, item.progressFraction()!!, 0.01f)
    }

    @Test
    fun progressFraction_noPosition_isNull() {
        val item = MediaItem(
            id = "i1",
            name = "Item",
            mediaType = MediaType.MOVIE,
            runTimeTicks = 600_000_000L,
        )

        assertEquals(null, item.progressFraction())
    }

    @Test
    fun progressFraction_pastRuntime_clampsToOne() {
        val item = MediaItem(
            id = "i1",
            name = "Item",
            mediaType = MediaType.MOVIE,
            runTimeTicks = 600_000_000L,
            playbackPositionTicks = 700_000_000L,
        )

        assertEquals(1.0f, item.progressFraction()!!, 0.001f)
    }
}
