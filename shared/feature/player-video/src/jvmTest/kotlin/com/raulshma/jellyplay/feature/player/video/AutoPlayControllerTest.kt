package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins [AutoPlayController] — the auto-advance state machine extracted from
 * `VideoPlayerViewModel`. Pure logic, no Android.
 */
class AutoPlayControllerTest {

    private val nextEpisode = MediaItem(id = "next", name = "Next", mediaType = MediaType.EPISODE)

    @Test
    fun defaults_disabledAndNotCancelled() {
        val c = AutoPlayController()
        assertFalse(c.enabled)
        assertFalse(c.cancelled)
    }

    @Test
    fun shouldAutoPlayNext_disabledByDefault() {
        val c = AutoPlayController()
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
    }

    @Test
    fun shouldAutoPlayNext_requiresEpisodeEnabledAndNotCancelled() {
        val c = AutoPlayController()
        assertFalse(c.shouldAutoPlayNext(null))

        c.setEnabled(true)
        assertTrue(c.shouldAutoPlayNext(nextEpisode))

        c.cancel()
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
    }

    @Test
    fun resetForNewItem_reArmsCountdown() {
        val c = AutoPlayController()
        c.setEnabled(true)
        c.cancel()
        assertFalse(c.shouldAutoPlayNext(nextEpisode))

        c.resetForNewItem()
        assertTrue(c.shouldAutoPlayNext(nextEpisode))
    }

    @Test
    fun canSkipToNext_ignoresCancelledBecauseUserActedDeliberately() {
        val c = AutoPlayController()
        c.setEnabled(true)
        c.cancel()
        // Natural end is blocked by the dismissal…
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
        // …but an explicit skip-credits press still advances.
        assertTrue(c.canSkipToNext(nextEpisode))
        assertFalse(c.canSkipToNext(null))
    }

    @Test
    fun canSkipToNext_requiresEnabled() {
        val c = AutoPlayController()
        assertFalse(c.canSkipToNext(nextEpisode))
        c.setEnabled(true)
        assertTrue(c.canSkipToNext(nextEpisode))
        c.setEnabled(false)
        assertFalse(c.canSkipToNext(nextEpisode))
    }

    @Test
    fun setEnabled_falseOnRelease() {
        val c = AutoPlayController()
        c.setEnabled(true)
        c.setEnabled(false)
        assertFalse(c.enabled)
        assertFalse(c.shouldAutoPlayNext(nextEpisode))
    }
}
