package com.raulshma.jellyplay.core.data.cast

import android.os.StrictMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [withCastDiskReadsPermitted]: the block runs with StrictMode
 * disk-read detection suspended, its value is returned, the previous thread
 * policy is restored even when the block throws, and the lenient policy is
 * visible inside the block.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CastDiskReadsTest {

    @Test
    fun `returns the block value`() {
        val value = withCastDiskReadsPermitted { "result" }

        assertEquals("result", value)
    }

    @Test
    fun `the lenient policy is active inside the block`() {
        val original = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyDeath().build(),
        )
        try {
            withCastDiskReadsPermitted {
                // Disk-read detection is suspended: the active policy is LAX.
                assertSame(StrictMode.ThreadPolicy.LAX, StrictMode.getThreadPolicy())
            }
        } finally {
            StrictMode.setThreadPolicy(original)
        }
    }

    @Test
    fun `the previous policy is restored after the block`() {
        val original = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyLog().build(),
        )
        try {
            withCastDiskReadsPermitted { Unit }

            assertEquals(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyLog().build().toString(),
                StrictMode.getThreadPolicy().toString(),
            )
        } finally {
            StrictMode.setThreadPolicy(original)
        }
    }

    @Test
    fun `the previous policy is restored even when the block throws`() {
        val original = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyLog().build(),
        )
        try {
            try {
                withCastDiskReadsPermitted<Unit> { throw IllegalStateException("boom") }
                throw AssertionError("expected the exception to propagate")
            } catch (expected: IllegalStateException) {
                assertSame("boom", expected.message)
            }

            assertEquals(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyLog().build().toString(),
                StrictMode.getThreadPolicy().toString(),
            )
        } finally {
            StrictMode.setThreadPolicy(original)
        }
    }
}
