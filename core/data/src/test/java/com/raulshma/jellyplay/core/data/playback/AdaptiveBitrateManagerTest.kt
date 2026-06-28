package com.raulshma.jellyplay.core.data.playback

import android.net.ConnectivityManager
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveBitrateManagerTest {

    // Relaxed mock: activeNetwork is null, so isUnmeteredConnection() reports a
    // metered connection — exactly the scenario where the cap matters most.
    private val connectivityManager: ConnectivityManager = mockk(relaxed = true)
    private val preferencesStore: UserPreferencesStore = mockk()

    private fun createManager(prefs: UserPreferences): AdaptiveBitrateManager {
        every { preferencesStore.preferences } returns MutableStateFlow(prefs)
        return AdaptiveBitrateManager(connectivityManager, preferencesStore)
    }

    @Test
    fun `AUTO with adaptive disabled sends no cap even on metered network`() {
        // Disabling adaptive bitrate must remove the cap so the server direct-
        // plays instead of transcoding high-bitrate media on metered links.
        val manager = createManager(UserPreferences(adaptiveBitrateEnabled = false))
        assertNull(manager.resolveMaxBitrate(StreamingQuality.AUTO))
    }

    @Test
    fun `AUTO with adaptive enabled caps at metered ceiling on metered network`() {
        val manager = createManager(UserPreferences(adaptiveBitrateEnabled = true))
        assertEquals(2_500_000L, manager.resolveMaxBitrate(StreamingQuality.AUTO))
    }

    @Test
    fun `AUTO with adaptive disabled but manual cap still honours manual cap`() {
        val manager = createManager(
            UserPreferences(adaptiveBitrateEnabled = false, manualBandwidthCap = 5_000_000L),
        )
        assertEquals(5_000_000L, manager.resolveMaxBitrate(StreamingQuality.AUTO))
    }
}
