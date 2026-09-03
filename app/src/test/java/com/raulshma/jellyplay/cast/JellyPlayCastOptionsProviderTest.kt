package com.raulshma.jellyplay.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the Cast session routing: the provider targets Google's DEFAULT_MEDIA
 * RECEIVER (video cast sessions must reach the standard receiver, not a
 * custom app id), and no additional session providers are registered — the
 * app's Jellyfin cast path rides the framework session entirely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class JellyPlayCastOptionsProviderTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `cast options target the default media receiver`() {
        val options = JellyPlayCastOptionsProvider().getCastOptions(context)

        assertEquals(
            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
            options.receiverApplicationId,
        )
    }

    @Test
    fun `no additional session providers are registered`() {
        assertTrue(
            JellyPlayCastOptionsProvider().getAdditionalSessionProviders(context) == null ||
                JellyPlayCastOptionsProvider().getAdditionalSessionProviders(context)!!.isEmpty(),
        )
    }

    @Test
    fun `options are rebuilt per call - the provider is stateless`() {
        val provider = JellyPlayCastOptionsProvider()
        val first = provider.getCastOptions(context)
        val second = provider.getCastOptions(context)

        assertEquals(first.receiverApplicationId, second.receiverApplicationId)
        assertNull(provider.getAdditionalSessionProviders(context))
    }
}
