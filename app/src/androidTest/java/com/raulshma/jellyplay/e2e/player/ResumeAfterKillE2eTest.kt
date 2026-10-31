package com.raulshma.jellyplay.e2e.player

import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import com.raulshma.jellyplay.core.model.PlayerType
import android.os.SystemClock
import org.junit.Before
import org.junit.Test

/**
 * Resume across process death: play, land the playhead mid-clip, let the
 * progress reporter persist it, `am force-stop` the app, relaunch via deep
 * link — the player must auto-resume near the saved position and the server's
 * ticks must agree with what the UI shows.
 */
@LargeTest
class ResumeAfterKillE2eTest : PlayerE2eBase() {

    private lateinit var tracksId: String

    @Before
    fun setUpEngineAndItem() {
        setPreferredPlayer(PlayerType.EXO_PLAYER)
        tracksId = fixture.itemId(PlayerE2eEnv.TRACKS_CLIP)
        fixture.resetUserData(tracksId)
    }

    @Test
    fun relaunchAfterForceStopResumesNearSavedPosition() {
        openPlayer(tracksId)
        // First pass: get playing, then seek to ~25% (60s of 240s) and let it
        // play long enough for a progress report to land on the server.
        waitFor("playback never started", timeoutMs = 60_000) {
            device.findObject(By.desc("Pause"))
        }
        showControls()
        val bar = device.findObject(By.res("player-seekbar"))
            ?: throw AssertionError("seekbar not addressable")
        val bounds = bar.visibleBounds
        tap(bounds.left + bounds.width() / 4, bounds.centerY())
        hideControls()
        SystemClock.sleep(5_000)

        val savedSeconds = waitFor(
            "server never recorded the pre-kill position",
            timeoutMs = 60_000,
        ) {
            val serverSeconds = fixture.playbackPositionSeconds(tracksId)
            serverSeconds.takeIf { it in 40..90 }
        }

        // Kill the app mid-playback (process death, not a graceful stop).
        shell("am force-stop ${PlayerE2eEnv.APP_ID}")
        SystemClock.sleep(2_000)

        // Relaunch via deep link: details now offer "Resume" — take it.
        openPlayer(tracksId)
        try {
            // The resume auto-seek runs during load; the first stable readout
            // must already be near the saved position (ResumeTicks semantics:
            // server ticks seed the seek bar BEFORE the loading screen lifts).
            val resumedSeconds = waitFor(
                "position after relaunch never settled near $savedSeconds",
                timeoutMs = 45_000,
            ) {
                showControls()
                val value = positionSeconds()
                hideControls()
                value?.takeIf { it in (savedSeconds - 15)..(savedSeconds + 20) }
            }

            // Server truth again after resume: the reporter keeps advancing.
            SystemClock.sleep(6_000)
            val afterResume = fixture.playbackPositionSeconds(tracksId)
            check(afterResume >= resumedSeconds - 20) {
                "server position ($afterResume) fell far behind the resumed playhead ($resumedSeconds)"
            }
        } finally {
            exitPlayer()
        }
    }
}
