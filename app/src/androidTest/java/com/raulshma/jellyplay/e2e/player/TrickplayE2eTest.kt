package com.raulshma.jellyplay.e2e.player

import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Gesture trickplay end-to-end — the regression net for 65c87a407 ("gesture
 * trickplay not working"): a live horizontal scrub on the video surface must
 * surface the trickplay overlay (Bubble/Filmstrip/Fullscreen presentation per
 * prefs), keep it up mid-drag, dismiss it on lift, and land the seek.
 *
 * Requires the fixture's trickplay tiles (bootstrap extracts them via the
 * scheduled task); skipped when extraction never completed, so tile lag can't
 * mask a real regression with a false red.
 */
@LargeTest
class TrickplayE2eTest : PlayerE2eBase() {

    private lateinit var tracksId: String

    @Before
    fun setUpItemAndTiles() {
        tracksId = fixture.itemId(PlayerE2eEnv.TRACKS_CLIP)
        fixture.resetUserData(tracksId)
        assumeTrue(
            "fixture has no trickplay tiles for '${PlayerE2eEnv.TRACKS_CLIP}' — re-run bootstrap-jellyfin.sh",
            fixture.hasTrickplay(tracksId),
        )
    }

    @Test
    fun gestureScrubShowsTrickplayOverlayThenLandsSeek() {
        openPlayer(tracksId)
        try {
            waitFor("playback never started", timeoutMs = 60_000) {
                device.findObject(By.desc("Pause"))
            }
            hideControls()

            val w = device.displayWidth
            val h = device.displayHeight
            var overlaySeen = false
            drag(
                startX = w / 6, startY = h / 2,
                endX = (w * 5) / 6, endY = h / 2,
                durationMs = 2_500,
                midDrag = { if (hasRes("player-trickplay-gesture-overlay")) overlaySeen = true },
            )

            check(overlaySeen) {
                "trickplay overlay never appeared during a live horizontal scrub"
            }
            waitForTruthy("trickplay overlay never dismissed after the drag") {
                !hasRes("player-trickplay-gesture-overlay")
            }

            // The committed seek: a 15%→83% sweep lands somewhere forward of
            // the start but inside the clip (the delta is scaled by the
            // swipe-seek cap, so assert a wide, sane band).
            showControls()
            val landed = positionSeconds()
            hideControls()
            checkNotNull(landed)
            check(landed in 15..220) { "scrub seek landed at ${landed}s, outside the plausible band" }
        } finally {
            exitPlayer()
        }
    }
}
