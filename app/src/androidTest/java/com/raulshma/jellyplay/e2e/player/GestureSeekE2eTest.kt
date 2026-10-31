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
 * The gesture tier, driven with timing-precise injected input (the double-tap
 * and mid-drag assertions are unreachable through UiDevice's coarse swipes):
 * horizontal drag seek, double-tap seek, brightness/volume edge bars, and the
 * long-press hold-speed pill. ExoPlayer full coverage + an mpv smoke of the
 * drag-seek path (the gesture detectors are engine-independent).
 */
@RunWith(Parameterized::class)
@LargeTest
class GestureSeekE2eTest(private val engine: PlayerType) : PlayerE2eBase() {

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

    private fun openTracksPlayer() {
        openPlayer(tracksId)
        waitFor("playback never started (engine=$engine)", timeoutMs = 60_000) {
            device.findObject(By.desc("Pause"))
        }
        hideControls()
    }

    private fun shownPositionSeconds(): Long? {
        showControls()
        val value = positionSeconds()
        hideControls()
        return value
    }

    @Test
    fun horizontalDragSeeks() {
        openTracksPlayer()
        val before = shownPositionSeconds() ?: throw AssertionError("no position readout")

        // Swipe right across the surface center: the drag-seek arm scales the
        // horizontal travel to a forward delta and commits on lift.
        val w = device.displayWidth
        val h = device.displayHeight
        drag(w / 5, h / 2, (w * 4) / 5, h / 2, durationMs = 900)
        SystemClock.sleep(1_500)

        val after = shownPositionSeconds() ?: throw AssertionError("no position readout after drag")
        check(after != before) { "horizontal drag did not change position ($before -> $after)" }
    }

    @Test
    fun doubleTapSeeksForwardAndBack() {
        openTracksPlayer()

        // From ~0: 3x double-tap right ≈ +30s (mobile double-tap seek = ±10s).
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(3) {
            doubleTap((w * 4) / 5, h / 2)
            SystemClock.sleep(1_200)
        }
        val forward = shownPositionSeconds() ?: throw AssertionError("no position readout")
        check(forward in 15..60) { "3x double-tap forward landed at ${forward}s, expected ~30s" }

        doubleTap(w / 5, h / 2)
        SystemClock.sleep(1_200)
        val back = shownPositionSeconds() ?: throw AssertionError("no position readout")
        check(back < forward) { "double-tap back did not move position back ($forward -> $back)" }
    }

    @Test
    fun verticalLeftDragShowsBrightnessOverlay() {
        openTracksPlayer()
        val w = device.displayWidth
        val h = device.displayHeight
        var overlaySeen = false
        drag(
            w / 4, h / 2, w / 4, h / 6,
            durationMs = 1_200,
            midDrag = { if (hasRes("player-brightness-overlay")) overlaySeen = true },
        )
        check(overlaySeen) { "brightness overlay never appeared during left-edge vertical drag" }
        waitForTruthy("brightness overlay never dismissed after gesture") { !hasRes("player-brightness-overlay") }
    }

    @Test
    fun verticalRightDragShowsVolumeOverlay() {
        openTracksPlayer()
        val w = device.displayWidth
        val h = device.displayHeight
        var overlaySeen = false
        drag(
            (w * 3) / 4, h / 6, (w * 3) / 4, h / 2,
            durationMs = 1_200,
            midDrag = { if (hasRes("player-volume-overlay")) overlaySeen = true },
        )
        check(overlaySeen) { "volume overlay never appeared during right-edge vertical drag" }
    }

    @Test
    fun longPressShowsHoldSpeedPillAndReleaseClearsIt() {
        openTracksPlayer()
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2

        longPress(cx, cy, holdMs = 1_200)
        waitForTruthy("hold-speed pill never appeared during long-press", timeoutMs = 5_000) {
            hasRes("player-hold-speed-pill")
        }
        // Released: the pill clears shortly after UP.
        waitForTruthy("hold-speed pill never cleared after release", timeoutMs = 5_000) {
            !hasRes("player-hold-speed-pill")
        }
    }
}
