package com.raulshma.jellyplay

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the launcher task contract between shell and playback host.
 *
 * MainActivity used to be `singleTask`. A singleTask root destroys every
 * activity above it whenever the launcher intent is re-delivered, so tapping
 * the app icon from the home screen while [PlayerActivity] was on top
 * (fullscreen playback, or the PiP task behind it) closed the player and
 * revealed the originating browse screen. `singleTop` is the documented
 * launcher-root behavior instead: the task comes to the front and its top
 * activity resumes.
 *
 * These pins fail if the manifest regresses either side of the contract:
 *  - MainActivity back to a task-initiating mode (`singleTask` /
 *    `singleInstance`), which resurrects the clear-above-on-launch bug;
 *  - MainActivity without `alwaysRetainTaskState`, which lets the system's
 *    timed (~30 min) task reset close the player above the root after a long
 *    pause;
 *  - PlayerActivity away from `singleTask`, which breaks the media-notification
 *    `onNewIntent` reuse of a live (backgrounded/PiP) instance that
 *    [PlayerActivityLockRedirectTest] exercises.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MainActivityTaskContractTest {

    @Test
    fun `MainActivity is a singleTop launcher root, never a task-initiating mode`() {
        val info = activityInfo(MainActivity::class.java)
        assertEquals(
            "MainActivity must stay singleTop: a singleTask root destroys the activities " +
                "above it on every launcher re-launch, which closes PlayerActivity and drops " +
                "the user on the browse screen they started playback from",
            ActivityInfo.LAUNCH_SINGLE_TOP,
            info.launchMode,
        )
    }

    @Test
    fun `MainActivity retains the task across a launcher re-selection`() {
        val info = activityInfo(MainActivity::class.java)
        assertTrue(
            "MainActivity (the task root) must set alwaysRetainTaskState so the system's " +
                "timed task reset cannot clear a paused player above it",
            info.flags and ActivityInfo.FLAG_ALWAYS_RETAIN_TASK_STATE != 0,
        )
    }

    @Test
    fun `PlayerActivity stays singleTask so notification taps reuse its live instance`() {
        assertEquals(
            "PlayerActivity must stay singleTask: the media-notification PendingIntent reaches " +
                "a backgrounded/PiP instance through onNewIntent rather than stacking a second " +
                "player",
            ActivityInfo.LAUNCH_SINGLE_TASK,
            activityInfo(PlayerActivity::class.java).launchMode,
        )
    }

    private fun activityInfo(clazz: Class<*>): ActivityInfo {
        val application = RuntimeEnvironment.getApplication()
        return application.packageManager.getActivityInfo(ComponentName(application, clazz), 0)
    }
}
