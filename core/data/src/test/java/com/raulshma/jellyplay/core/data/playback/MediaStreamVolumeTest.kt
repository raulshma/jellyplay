package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStreamVolumeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun getNormalized_returnsNormalizedVolumeBetweenZeroAndOne() {
        val volume = MediaStreamVolume.getNormalized(context)
        assertTrue("Volume should be >= 0", volume >= 0f)
        assertTrue("Volume should be <= 1", volume <= 1f)
    }

    @Test
    fun setNormalized_updatesStreamVolumeWithoutErrors() {
        MediaStreamVolume.setNormalized(context, 0.5f)
        val updated = MediaStreamVolume.getNormalized(context)
        assertTrue("Volume should be non-negative", updated >= 0f)
    }
}
