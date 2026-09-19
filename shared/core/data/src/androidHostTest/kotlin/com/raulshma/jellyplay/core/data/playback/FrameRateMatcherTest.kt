package com.raulshma.jellyplay.core.data.playback

import android.app.Activity
import com.raulshma.jellyplay.core.model.RefreshRateMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [FrameRateMatcher]'s guard rails — the paths that must leave the host
 * window untouched (Robolectric's shadow display exposes no supported-mode
 * list, so the actual mode-pick delegation to the shared [RefreshRateMatcher]
 * is exercised in the shared module's tests, not here):
 *
 * - `RefreshRateMode.OFF`, a null frame rate and a non-positive frame rate all
 *   return before touching the window attributes.
 * - `restoreOriginalMode` without a previously matched mode is a no-op.
 * - Guards run before the original mode is recorded, so a no-op call must not
 *   make a later `restoreOriginalMode` write anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrameRateMatcherTest {

    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun preferredModeId(activity: Activity): Int =
        activity.window.attributes.preferredDisplayModeId

    @Test
    fun `OFF mode never touches the window attributes`() {
        val activity = activity()

        FrameRateMatcher.matchFrameRate(activity, frameRate = 24f, mode = RefreshRateMode.OFF)
        FrameRateMatcher.restoreOriginalMode(activity)

        assertEquals(0, preferredModeId(activity))
    }

    @Test
    fun `a null frame rate is a no-op`() {
        val activity = activity()

        FrameRateMatcher.matchFrameRate(activity, frameRate = null, mode = RefreshRateMode.FRAME_RATE_ONLY)

        assertEquals(0, preferredModeId(activity))
    }

    @Test
    fun `a non-positive frame rate is a no-op`() {
        val activity = activity()

        FrameRateMatcher.matchFrameRate(activity, frameRate = 0f, mode = RefreshRateMode.FRAME_RATE_ONLY)
        FrameRateMatcher.matchFrameRate(activity, frameRate = -30f, mode = RefreshRateMode.FRAME_RATE_ONLY)

        assertEquals(0, preferredModeId(activity))
    }

    @Test
    fun `no-op matches never arm the restore path`() {
        val activity = activity()

        // All three guard out before recording the original mode.
        FrameRateMatcher.matchFrameRate(activity, frameRate = 0f, mode = RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
        FrameRateMatcher.matchFrameRate(activity, frameRate = null, mode = RefreshRateMode.FRAME_RATE_AND_RESOLUTION)
        FrameRateMatcher.matchFrameRate(activity, frameRate = 60f, mode = RefreshRateMode.OFF)
        FrameRateMatcher.restoreOriginalMode(activity)

        assertEquals(0, preferredModeId(activity))
    }
}
