package com.raulshma.jellyplay.core.data.playback

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveBitrateManager @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val userPreferencesStore: UserPreferencesStore,
) {
    fun resolveMaxBitrate(quality: StreamingQuality): Long? {
        val prefs = userPreferencesStore.preferences.value

        // Check metered network behavior
        if (!isUnmeteredConnection()) {
            if (prefs.meteredNetworkBehavior == MeteredNetworkBehavior.BLOCK) {
                return 1L
            }
        }

        val resolved = when (quality) {
            StreamingQuality.AUTO -> {
                if (!prefs.adaptiveBitrateEnabled) {
                    MAX_BITRATE_720P
                } else if (isUnmeteredConnection()) {
                    null
                } else {
                    MAX_BITRATE_METERED
                }
            }
            StreamingQuality.LOW_360P -> MAX_BITRATE_360P
            StreamingQuality.SD_480P -> MAX_BITRATE_480P
            StreamingQuality.HD_720P -> MAX_BITRATE_720P
            StreamingQuality.FHD_1080P -> MAX_BITRATE_1080P
            StreamingQuality.UHD_4K -> null
        }

        val cap = prefs.manualBandwidthCap
        return if (cap > 0L) {
            if (resolved != null) {
                kotlin.math.min(resolved, cap)
            } else {
                cap
            }
        } else {
            resolved
        }
    }

    fun isUnmeteredConnection(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    companion object {
        private const val MAX_BITRATE_METERED = 2_500_000L
        private const val MAX_BITRATE_360P = 500_000L
        private const val MAX_BITRATE_480P = 1_500_000L
        private const val MAX_BITRATE_720P = 3_000_000L
        private const val MAX_BITRATE_1080P = 8_000_000L
    }
}
