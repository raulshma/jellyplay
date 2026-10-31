package com.raulshma.jellyplay.e2e.player

import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.PlayerType
import android.os.SystemClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext

/**
 * PiP + background-audio behavior on the real activity:
 *  - the in-app PiP button pins the activity (mode=pinned), playback keeps
 *    running, and the PiP remote actions (play/pause) drive the session;
 *  - with auto-PiP off, HOME backgrounds the app while the media session
 *    keeps playing, and a deep link brings the player back.
 */
@LargeTest
class PipAndBackgroundE2eTest : PlayerE2eBase() {

    private lateinit var tracksId: String

    @Before
    fun setUpItem() {
        setPreferredPlayer(PlayerType.EXO_PLAYER)
        tracksId = fixture.itemId(PlayerE2eEnv.TRACKS_CLIP)
        fixture.resetUserData(tracksId)
    }

    // ── device-state probes ───────────────────────────────────────────────

    private fun isPinned(): Boolean =
        shell("dumpsys activity activities").contains("mode=pinned")

    private fun sessionState(): String? {
        val dump = shell("dumpsys media_session")
        // OEM formatting varies: "state=PlaybackState.STATE_PLAYING(3)" or
        // legacy "state=PLAYING(3)" — both end in "PLAYING(3)" / "PAUSED(2)".
        return when {
            dump.contains("PLAYING(3)") -> "playing"
            dump.contains("PAUSED(2)") -> "paused"
            else -> null
        }
    }

    /** The player window's node — in PiP, its bounds ARE the pip window. */
    private fun playerRoot(): UiObject2? = device.findObject(By.res("player-root"))

    private fun exitPipToFullscreen() {
        // Try the system PiP menu's expand control first (desc is OEM-flavored).
        playerRoot()?.let { window ->
            window.click()
            SystemClock.sleep(1_000)
            for (desc in listOf("Full screen", "Fullscreen", "Expand", "Maximize")) {
                val expand = device.findObject(By.desc(desc))
                if (expand != null && expand.isClickable) {
                    expand.click()
                    if (waitForTruthy(timeoutMs = 8_000) { !isPinned() }) return
                }
            }
        }
        // Fallback (device-pip-pass measured BACK as the reliable dismiss):
        // BACK dismisses the pip window (activity stays alive); a deep link
        // brings the player back fullscreen.
        device.pressBack()
        waitForTruthy("pip window never dismissed on back", timeoutMs = 10_000) { !isPinned() }
        openPlayer(tracksId)
    }

    @Test
    fun pipButtonPinsActivityAndRemoteActionsDriveSession() {
        openPlayer(tracksId)
        try {
            waitFor("playback never started", timeoutMs = 60_000) {
                device.findObject(By.desc("Pause"))
            }

            showControls()
            device.findObject(By.res("player-pip-button"))?.click()
                ?: throw AssertionError("PiP button not found in the control bar")
            waitForTruthy("activity never entered PiP (mode=pinned)", timeoutMs = 20_000) { isPinned() }
            waitForTruthy("media session not PLAYING while pinned", timeoutMs = 15_000) {
                sessionState() == "playing"
            }

            // Remote actions: single tap on the pip window reveals the action
            // row; the play/pause fork carries "Pause"/"Play" titles.
            playerRoot()?.click()
            val pauseAction = waitFor("PiP remote actions never appeared", timeoutMs = 8_000) {
                device.findObject(By.desc("Pause"))
            }
            pauseAction.click()
            waitForTruthy("session never paused via PiP action", timeoutMs = 15_000) {
                sessionState() == "paused"
            }

            playerRoot()?.click()
            val playAction = waitFor("PiP play action never appeared", timeoutMs = 8_000) {
                device.findObject(By.desc("Play"))
            }
            playAction.click()
            waitForTruthy("session never resumed via PiP action", timeoutMs = 15_000) {
                sessionState() == "playing"
            }

            exitPipToFullscreen()
            waitForTruthy("playback not restored after leaving PiP", timeoutMs = 15_000) {
                sessionState() == "playing"
            }
        } finally {
            if (isPinned()) device.pressBack()
            runCatching { exitPlayer() }
        }
    }

    @Test
    fun homeWithAutoPipOffKeepsAudioPlayingAndRelaunchRestores() {
        // Auto-PiP ON (default) turns HOME into a PiP entry — this test wants
        // the plain-background arm, so flip the pref through the app's store.
        val store = GlobalContext.get().get<PlaybackStore>()
        runBlocking { store.setAutoEnterPip(false) }
        try {
            openPlayer(tracksId)
            waitFor("playback never started", timeoutMs = 60_000) {
                device.findObject(By.desc("Pause"))
            }

            pressHome()
            SystemClock.sleep(2_000)
            waitForTruthy("media session not PLAYING while backgrounded", timeoutMs = 15_000) {
                sessionState() == "playing"
            }
            val notifDump = shell("dumpsys notification --noredact")
            assertTrue(
                "no media notification while backgrounded",
                notifDump.contains(PlayerE2eEnv.APP_ID),
            )

            // Return: the deep link re-enters the (still running) player.
            openPlayer(tracksId)
            waitForTruthy("player window not restored after relaunch", timeoutMs = 20_000) {
                hasRes("player-root")
            }
            waitForTruthy("session not PLAYING after return", timeoutMs = 15_000) {
                sessionState() == "playing"
            }
        } finally {
            runBlocking { store.setAutoEnterPip(true) }
            runCatching { exitPlayer() }
        }
    }
}
