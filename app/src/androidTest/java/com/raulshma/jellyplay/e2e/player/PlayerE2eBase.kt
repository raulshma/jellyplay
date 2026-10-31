package com.raulshma.jellyplay.e2e.player

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.PlayerType
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.GlobalContext

/**
 * Shared harness for the on-device video-player E2E suite.
 *
 * Contract with the host runner (tools/e2e/run-player-e2e.sh):
 *  - The device reaches the fixture at [PlayerE2eEnv.serverUrl]
 *    (default loopback via `adb reverse tcp:8096`).
 *  - The runner does the once-per-device prep (wake, keyguard, pm grant,
 *    autofill off, optional `pm clear` for a hermetic run). The harness is
 *    idempotent on top of any of those states: it signs in only when the
 *    first-run screen is up.
 *
 * UI addressing: the player window opts into `testTagsAsResourceId`
 * (PlayerActivity), so tagged nodes resolve via `By.res("player-…")`.
 * Pre-player screens (login, details) are text/contentDescription driven —
 * they are stable English strings from the fixture's en-US culture.
 */
abstract class PlayerE2eBase {

    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val device: UiDevice = UiDevice.getInstance(instrumentation)
    protected val fixture = PlayerFixtureClient(PlayerE2eEnv.serverUrl)

    /** Screenshot + view hierarchy on failure, for the runner to pull. */
    @get:Rule
    val artifactWatcher = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            saveFailureArtifacts(description.methodName)
        }
    }

    /** Every test starts from the signed-in foreground state. */
    @org.junit.Before
    fun baseSetUp() {
        ensureSignedIn()
    }

    // ── device-level prep ─────────────────────────────────────────────────

    /** Shell command with shell-identity privileges (UiAutomation channel). */
    protected fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                .readBytes().toString(Charsets.UTF_8)
        }

    // ── app entry ─────────────────────────────────────────────────────────

    /** Cold-start to the app's foreground state (signed-in home OR first run). */
    protected fun launchApp() {
        val intent = Intent(Intent.ACTION_MAIN)
            .setPackage(PlayerE2eEnv.APP_ID)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        instrumentation.targetContext.startActivity(intent)
        device.waitForIdle(5_000)
        SystemClock.sleep(1_500)
    }

    /**
     * Idempotent sign-in against the fixture. Drives the same first-run path
     * the shell passes do (Add Server → Connect → Sign In → Skip onboarding);
     * no-op when the app already has an active session.
     */
    protected fun ensureSignedIn() {
        launchApp()
        if (!device.hasObject(By.text("Add Server")) && !device.hasObject(By.text("Connect"))) {
            return // already signed in (or mid-app) — nothing to do
        }
        // Add Server screen: first EditText is the address field.
        val address = waitFor { device.findObject(By.clazz("android.widget.EditText")) }
        setTextField(address, PlayerE2eEnv.serverUrl)

        waitForClickableText("Connect").click()
        SystemClock.sleep(2_000)

        // Login screen: first two EditTexts are username / password.
        val fields = waitFor(timeoutMs = 20_000) {
            device.findObjects(By.clazz("android.widget.EditText")).takeIf { it.size >= 2 }
        }
        setTextField(fields[0], PlayerE2eEnv.FIXTURE_USER)
        setTextField(fields[1], PlayerE2eEnv.FIXTURE_PASSWORD)

        waitForClickableText("Sign In").click()
        // Post-login onboarding carousel: dismiss if present.
        val skipped = device.wait(Until.hasObject(By.text("Skip")), 20_000)
        if (skipped) {
            device.findObject(By.text("Skip"))?.click()
        }
        device.waitForIdle(5_000)
    }

    /**
     * Fill a Compose TextField: ACTION_SET_TEXT first (precise), falling back
     * to tap + `input text` (the shell passes' proven path) when the field
     * reports a different value afterwards.
     */
    private fun setTextField(field: UiObject2, value: String) {
        field.click()
        SystemClock.sleep(300)
        field.text = value
        SystemClock.sleep(300)
        if (field.text != value) {
            // `input text` takes the string verbatim (colons/slashes are
            // literal); only spaces need the %s escape.
            shell("input text ${value.replace(" ", "%s")}")
            SystemClock.sleep(300)
        }
    }

    private fun waitForClickableText(text: String): UiObject2 =
        waitFor {
            // Match by text or contentDescription; clickable ancestors win.
            device.findObjects(By.text(text)).firstOrNull { it.isClickable }
                ?: device.findObjects(By.desc(text)).firstOrNull { it.isClickable }
                ?: device.findObject(By.text(text))
        }

    // ── navigation into the player ────────────────────────────────────────

    /** Deep-link to the item's DETAILS screen (jellyplay://media/{id}). */
    protected fun openDetails(itemId: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("jellyplay://media/$itemId"))
            .setPackage(PlayerE2eEnv.APP_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        instrumentation.targetContext.startActivity(intent)
        // The details' Play/Resume button appears once metadata loads.
        waitFor(timeoutMs = 30_000) {
            device.findObjects(By.text("Play")).ifEmpty { device.findObjects(By.text("Resume")) }.firstOrNull()
        }
        SystemClock.sleep(500)
    }

    /** Details → Play/Resume → wait until the PlayerActivity window is live. */
    protected fun openPlayer(itemId: String, timeoutMs: Long = 60_000): UiObject2 {
        openDetails(itemId)
        val playButton = device.findObjects(By.text("Play")).firstOrNull { it.isClickable }
            ?: device.findObjects(By.text("Resume")).firstOrNull { it.isClickable }
            ?: device.findObjects(By.desc("Play")).firstOrNull { it.isClickable }
            ?: throw AssertionError("no Play/Resume control on details for item $itemId")
        playButton.click()
        val playerRoot = device.wait(Until.hasObject(By.res("player-root")), timeoutMs)
        assertTrue("player window never appeared for item $itemId", playerRoot)
        return device.findObject(By.res("player-root"))
    }

    /** Player is up; leave via BACK and wait for the player window to close. */
    protected fun exitPlayer() {
        device.pressBack()
        device.wait(Until.gone(By.res("player-root")), 15_000)
    }

    // ── engine preference (in-process Koin) ───────────────────────────────

    /** Write `preferred_player` through the app's own store — no Settings-UI coupling. */
    protected fun setPreferredPlayer(type: PlayerType) {
        val store = GlobalContext.get().get<PlaybackStore>()
        runBlocking { store.setPreferredPlayer(type) }
    }

    // ── player-state reads ────────────────────────────────────────────────

    /** The playback controls' current visibility, by probe of the play/pause button. */
    protected fun controlsVisible(): Boolean = device.hasObject(By.res("player-play-pause"))

    /** Show the control chrome (single tap on the surface toggles it). */
    protected fun showControls() {
        if (!controlsVisible()) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            waitFor { device.findObject(By.res("player-play-pause")) }
        }
    }

    protected fun hideControls() {
        if (controlsVisible()) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            device.wait(Until.gone(By.res("player-play-pause")), 5_000)
        }
    }

    /** "m:ss" / "h:mm:ss" → seconds; null when the label is mid-recomposition garbage. */
    protected fun parseClockSeconds(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(":").map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> null
        }
    }

    protected fun positionText(): String? =
        device.findObject(By.res("player-position-text"))?.text

    protected fun positionSeconds(): Long? = parseClockSeconds(positionText())

    /** True once the playhead has crossed [seconds] (controls shown briefly). */
    protected fun waitForPositionAtLeast(seconds: Long, timeoutMs: Long = 60_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            showControls()
            val value = positionSeconds()
            hideControls()
            if (value != null && value >= seconds) return
            SystemClock.sleep(1_000)
        }
        throw AssertionError("position never reached ${seconds}s (last: ${positionText()})")
    }

    // ── injected input (timing-precise gestures) ──────────────────────────

    private fun injectMotion(action: Int, x: Float, y: Float, downTime: Long) {
        val event = android.view.MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, x, y, 0,
        )
        try {
            instrumentation.uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    protected fun tap(x: Int, y: Int) {
        val now = SystemClock.uptimeMillis()
        injectMotion(android.view.MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), now)
        SystemClock.sleep(60)
        injectMotion(android.view.MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), now)
    }

    protected fun doubleTap(x: Int, y: Int) {
        tap(x, y)
        SystemClock.sleep(80)
        tap(x, y)
    }

    protected fun longPress(x: Int, y: Int, holdMs: Long = 900) {
        val now = SystemClock.uptimeMillis()
        injectMotion(android.view.MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), now)
        SystemClock.sleep(holdMs)
        injectMotion(android.view.MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), now)
    }

    /**
     * Slow drag with observer hooks: [midDrag] runs BETWEEN move events, so
     * tests can assert overlay state while the gesture is live (the trickplay
     * overlay exists only mid-drag).
     */
    protected fun drag(
        startX: Int, startY: Int, endX: Int, endY: Int,
        durationMs: Long = 600, stepMs: Long = 40,
        midDrag: (() -> Unit)? = null,
    ) {
        val now = SystemClock.uptimeMillis()
        injectMotion(android.view.MotionEvent.ACTION_DOWN, startX.toFloat(), startY.toFloat(), now)
        val steps = (durationMs / stepMs).coerceIn(1, 240)
        for (i in 1..steps) {
            SystemClock.sleep(stepMs)
            val x = startX + (endX - startX) * i / steps
            val y = startY + (endY - startY) * i / steps
            injectMotion(android.view.MotionEvent.ACTION_MOVE, x.toFloat(), y.toFloat(), now)
            midDrag?.invoke()
        }
        SystemClock.sleep(60)
        injectMotion(android.view.MotionEvent.ACTION_UP, endX.toFloat(), endY.toFloat(), now)
    }

    // ── waits ─────────────────────────────────────────────────────────────

    /** Poll-based wait on a lambda (the suite's only sanctioned "sleep"). */
    protected fun <T> waitFor(timeoutMs: Long = 15_000, pollMs: Long = 250, condition: () -> T?): T {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var last: T? = null
        while (SystemClock.uptimeMillis() < deadline) {
            last = condition()
            if (last != null) return last
            SystemClock.sleep(pollMs)
        }
        throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    /** Same as [waitFor] with a failure message naming the stuck step. */
    protected fun <T> waitFor(message: String, timeoutMs: Long = 15_000, pollMs: Long = 250, condition: () -> T?): T =
        try {
            waitFor(timeoutMs, pollMs, condition)
        } catch (e: AssertionError) {
            throw AssertionError(message, e)
        }

    protected fun waitForTruthy(timeoutMs: Long = 15_000, pollMs: Long = 250, condition: () -> Boolean): Boolean {
        waitFor(timeoutMs, pollMs) { if (condition()) Unit else null }
        return true
    }

    protected fun waitForTruthy(message: String, timeoutMs: Long = 15_000, pollMs: Long = 250, condition: () -> Boolean): Boolean {
        waitFor(message, timeoutMs, pollMs) { if (condition()) Unit else null }
        return true
    }

    protected fun hasRes(id: String): Boolean = device.hasObject(By.res(id))

    // ── failure artifacts ─────────────────────────────────────────────────

    /**
     * Screenshot + uiautomator hierarchy into the app's external files dir —
     * the runner pulls the whole e2e-artifacts folder into tools/e2e/.results/.
     */
    protected fun saveFailureArtifacts(name: String) {
        runCatching {
            val dir = instrumentation.targetContext.getExternalFilesDir(null)
                ?.resolve("e2e-artifacts") ?: return
            dir.mkdirs()
            val stamp = System.currentTimeMillis()
            device.takeScreenshot(File(dir, "$name-$stamp.png"), 0.5f, 16)
            device.dumpWindowHierarchy(File(dir, "$name-$stamp.xml"))
            FileOutputStream(File(dir, "$name-$stamp.log")).use { out ->
                out.write((listOf("server=$PlayerE2eEnv.serverUrl", "package=${PlayerE2eEnv.APP_ID}").joinToString("\n")).toByteArray())
            }
        }
    }

    // ── shared smaller helpers ────────────────────────────────────────────

    protected fun center() = (device.displayWidth / 2) to (device.displayHeight / 2)

    protected fun pressHome() = device.pressHome()
}
