package com.raulshma.jellyplay.e2e.player

import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import android.os.SystemClock
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The session flow around episodes: playing E01 into its final stretch arms
 * the Up-Next card (countdown + next-episode identity), and tapping
 * "play next" advances to E02 with the player title bar confirming the swap.
 */
@LargeTest
class UpNextSessionE2eTest : PlayerE2eBase() {

    private lateinit var episode1: String

    @Before
    fun setUpItem() {
        episode1 = fixture.episodeId(PlayerE2eEnv.EPISODE_1)
        fixture.resetUserData(episode1)
        fixture.resetUserData(fixture.episodeId(PlayerE2eEnv.EPISODE_2))
    }

    @Test
    fun upNextCardArmsNearEndAndPlayNextAdvancesToEpisode2() {
        openPlayer(episode1)
        try {
            waitFor("E01 playback never started", timeoutMs = 60_000) {
                device.findObject(By.desc("Pause"))
            }

            // Seek into the final stretch (~88% of 150s): the tap commits the
            // fraction x duration, past every auto-play threshold.
            showControls()
            val bar = device.findObject(By.res("player-seekbar"))
                ?: throw AssertionError("seekbar not addressable")
            val bounds = bar.visibleBounds
            val x = bounds.left + (bounds.width() * 88) / 100
            tap(x, bounds.centerY())
            hideControls()

            // The Up-Next card arms with the countdown (autoplay defaults on).
            val upNext = waitFor("Up-Next card never armed near the end of E01", timeoutMs = 60_000) {
                device.findObject(By.res("player-up-next-card"))
            }
            val hasCountdown = device.hasObject(By.res("player-up-next-countdown"))
            val identity = upNext.text ?: ""
            check(identity.contains("Harness E02") || hasCountdown) {
                "Up-Next card is up but carries neither the countdown nor the E02 identity (text=$identity)"
            }

            // Manual advance: tap the card's play button.
            val playNext = device.findObject(By.res("player-up-next-play"))
                ?: throw AssertionError("Up-Next play button not rendered")
            playNext.click()

            // The title bar (controls visible) names the new episode.
            waitFor("E02 never became the playing item after Play Next", timeoutMs = 45_000) {
                showControls()
                val title = device.findObject(By.res("player-title"))?.text ?: return@waitFor null
                if (title.contains("Harness E02")) title else null
            }
            assertTrue(
                "still-watching confirm shown unexpectedly",
                !device.hasObject(By.text("Still watching?")),
            )
        } finally {
            exitPlayer()
        }
    }
}
