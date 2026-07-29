package com.raulshma.jellyplay.feature.player.video.engine

import android.app.ActivityManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `EngineDeviceProfile.isLowRamDevice` drives the trickplay cache budget. We
 * mock [Context]/[ActivityManager] directly (this class has no Android
 * framework state worth a Robolectric fixture) to assert each branch of the
 * OR: the platform `isLowRamDevice` flag and the memoryClass threshold.
 */
class EngineDeviceProfileTest {

    private fun contextWith(am: ActivityManager?): Context = mockk {
        every { getSystemService(Context.ACTIVITY_SERVICE) } returns am
    }

    @Test
    fun isLowRamDevice_trueWhenPlatformFlagSet() {
        val am = mockk<ActivityManager> {
            every { isLowRamDevice } returns true
            every { memoryClass } returns 512
        }
        assertTrue(EngineDeviceProfile.isLowRamDevice(contextWith(am)))
    }

    @Test
    fun isLowRamDevice_trueWhenMemoryClassAtOrBelowThreshold() {
        val am = mockk<ActivityManager> {
            every { isLowRamDevice } returns false
            every { memoryClass } returns 256
        }
        assertTrue(EngineDeviceProfile.isLowRamDevice(contextWith(am)))
    }

    @Test
    fun isLowRamDevice_falseWhenMemoryClassAboveThreshold() {
        val am = mockk<ActivityManager> {
            every { isLowRamDevice } returns false
            every { memoryClass } returns 257
        }
        assertFalse(EngineDeviceProfile.isLowRamDevice(contextWith(am)))
    }

    @Test
    fun isLowRamDevice_falseWhenAmHighMemoryAndFlagOff() {
        val am = mockk<ActivityManager> {
            every { isLowRamDevice } returns false
            every { memoryClass } returns 512
        }
        assertFalse(EngineDeviceProfile.isLowRamDevice(contextWith(am)))
    }

    @Test
    fun isLowRamDevice_returnsFalseWhenActivityManagerMissing() {
        // getSystemService returns null (cast as? → null) → guard returns false.
        assertFalse(EngineDeviceProfile.isLowRamDevice(contextWith(am = null)))
    }
}
