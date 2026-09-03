package com.raulshma.jellyplay

import android.os.StrictMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke-pins the debug-source-set StrictMode install that
 * [JellyPlayApplication.attachBaseContext] runs before ContentProviders:
 * it must install both a thread policy and a VM policy (diagnostic
 * penaltyLog/penaltyFlashScreen — deliberately NOT penaltyDeath) and never
 * throw, so a policy regression fails here instead of at cold start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DebugStrictModeTest {

    @Test
    fun `installing the debug policies succeeds and leaves both policies configured`() {
        installDebugStrictMode()

        // getThreadPolicy/getVmPolicy always return a policy object; a broken
        // install (exception) fails the call above, and an empty install would
        // leave the unconfigured detect Nothing defaults below.
        val threadPolicy = StrictMode.getThreadPolicy()
        val vmPolicy = StrictMode.getVmPolicy()
        assertTrue(threadPolicy !== StrictMode.ThreadPolicy.LAX)
        assertTrue(vmPolicy !== StrictMode.VmPolicy.LAX)
    }
}
