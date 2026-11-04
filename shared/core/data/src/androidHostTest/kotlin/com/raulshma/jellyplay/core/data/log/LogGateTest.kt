package com.raulshma.jellyplay.core.data.log

import android.util.Log as AndroidLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

private const val TAG = "LogGateTest"

/**
 * Pins the Android [Log] facade's runtime gate invariants (Robolectric
 * shadows the underlying `android.util.Log`, so delegated calls are
 * observable via [ShadowLog.getLogsForTag]):
 *
 * 1. `minimumLevel` defaults to `android.util.Log.WARN` — release builds
 *    suppress d/i out of the box, before the app shell configures anything.
 * 2. `d`/`i` at WARN return 0 and never reach logcat (no log item recorded),
 *    for both the plain and throwable-taking `d` overloads.
 * 3. `setVerboseLoggingEnabled(true)` raises the gate to
 *    `android.util.Log.DEBUG` and d/i then delegate (log items recorded);
 *    the setter is idempotent in both directions.
 * 4. `w`/`e` always delegate regardless of the gate — warnings/errors are
 *    never suppressed.
 *
 * @Before/@After re-pin the gate to the release default: the facade is a
 * process-wide singleton, and a raised gate must not leak into sibling test
 * classes sharing the worker JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogGateTest {

    @Before
    fun resetGateAndLogs() {
        ShadowLog.reset()
        Log.setVerboseLoggingEnabled(false)
    }

    @After
    fun restoreReleaseDefault() {
        Log.setVerboseLoggingEnabled(false)
        ShadowLog.reset()
    }

    @Test
    fun minimumLevelDefaultsToWarn() {
        assertEquals(AndroidLog.WARN, Log.minimumLevel)
    }

    @Test
    fun dAndIAreSuppressedAtWarn() {
        assertEquals(0, Log.d(TAG, "debug message"))
        assertEquals(0, Log.d(TAG, "debug message", RuntimeException("boom")))
        assertEquals(0, Log.i(TAG, "info message"))
        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty())
    }

    @Test
    fun verboseLoggingRaisesGateAndDelegates() {
        Log.setVerboseLoggingEnabled(true)
        assertEquals(AndroidLog.DEBUG, Log.minimumLevel)
        Log.d(TAG, "debug message")
        Log.i(TAG, "info message")
        val logged = ShadowLog.getLogsForTag(TAG)
        assertEquals(2, logged.size)
        assertEquals(AndroidLog.DEBUG, logged[0].type)
        assertEquals(AndroidLog.INFO, logged[1].type)
    }

    @Test
    fun setVerboseLoggingEnabledIsIdempotentInBothDirections() {
        Log.setVerboseLoggingEnabled(true)
        Log.setVerboseLoggingEnabled(true)
        assertEquals(AndroidLog.DEBUG, Log.minimumLevel)
        Log.setVerboseLoggingEnabled(false)
        Log.setVerboseLoggingEnabled(false)
        assertEquals(AndroidLog.WARN, Log.minimumLevel)
    }

    @Test
    fun wAndEAlwaysDelegateRegardlessOfGate() {
        Log.w(TAG, "warn message")
        Log.w(TAG, RuntimeException("boom"))
        Log.w(TAG, "warn message", RuntimeException("boom"))
        Log.e(TAG, "error message")
        Log.e(TAG, "error message", RuntimeException("boom"))
        val logged = ShadowLog.getLogsForTag(TAG)
        assertEquals(5, logged.size)
        assertTrue(logged.all { it.type >= AndroidLog.WARN })
    }
}
