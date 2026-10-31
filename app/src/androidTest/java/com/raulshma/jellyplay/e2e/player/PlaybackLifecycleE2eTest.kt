package com.raulshma.jellyplay.e2e.player

import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.raulshma.jellyplay.core.model.PlayerType
import android.os.SystemClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Playback lifecycle against the real fixture: launch → ready → position
 * advances → pause freezes → seek lands → the server sees the progress.
 * Parameterized over the in-app engines the suite promises to cover
 * (ExoPlayer + mpv; LibVLC is covered at unit level only, by decision).
 */
@RunWith(Parameterized::class)
@LargeTest
class PlaybackLifecycleE2eTest(private val engine: PlayerType) : PlayerE2eBase() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun engines(): List<PlayerType> = listOf(PlayerType.EXO_PLAYER, PlayerType.MPV)
    }

    private lateinit var tracksId: String

    @Before
    fun setUpEngineAndItem() {
        setPreferredPlayer(engine)
        tracksId = fixture.itemId(PlayerE2eEnv.TRACKS_CLIP)
        fixture.resetUserData(tracksId)
    }

    @Test
    fun playAdvancePauseSeekAndServerReporting() {
        openPlayer(tracksId)
        try {
            // Ready = chrome shows the "Pause" desc (only rendered while playing).
            waitFor("playback never started", timeoutMs = 60_000) {
                device.findObject(By.desc("Pause"))
            }

            // Position advances while playing.
            showControls()
            val at0 = positionSeconds()
            hideControls()
            SystemClock.sleep(3_500)
            showControls()
            val at1 = positionSeconds()
            hideControls()
            checkNotNull(at0); checkNotNull(at1)
            check(at1 > at0) { "position did not advance: $at0 -> $at1 (engine=$engine)" }

            // Pause freezes the playhead.
            showControls()
            device.findObject(By.desc("Pause"))?.click()
                ?: throw AssertionError("Pause control gone while playing")
            SystemClock.sleep(1_000)
            val pausedAt = positionSeconds()
            SystemClock.sleep(2_500)
            val stillPausedAt = positionSeconds()
            checkNotNull(pausedAt); checkNotNull(stillPausedAt)
            check(stillPausedAt - pausedAt <= 1) {
                "position kept advancing while paused: $pausedAt -> $stillPausedAt"
            }

            // Seek: tap the seekbar's midpoint (tap = drag down+up at fraction;
            // the bar commits the fraction x duration on lift).
            val bar = device.findObject(By.res("player-seekbar"))
                ?: throw AssertionError("seekbar not addressable while controls visible")
            val bounds = bar.visibleBounds
            tap(bounds.left + bounds.width() / 2, bounds.centerY())
            SystemClock.sleep(1_500)
            val afterSeek = positionSeconds()
            checkNotNull(afterSeek)
            check(afterSeek in 100..140) {
                "midpoint seek landed at ${afterSeek}s, expected ~120s ±20"
            }

            // Server truth: resume playback so the periodic reporter pushes the
            // new position to the fixture, then assert the ticks landed.
            device.findObject(By.desc("Play"))?.click()
                ?: throw AssertionError("Play control gone while paused post-seek")
            waitFor("playback never resumed after seek", timeoutMs = 30_000) {
                device.findObject(By.desc("Pause"))
            }
            waitForTruthy("server never saw progress after the seek", timeoutMs = 60_000) {
                fixture.playbackPositionSeconds(tracksId) >= 90
            }
        } finally {
            exitPlayer()
        }
    }
}
