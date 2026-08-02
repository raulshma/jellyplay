package com.raulshma.jellyplay.core.data.playback

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveBitrateManager @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val networkOfflineStore: NetworkOfflineStore,
    private val playbackStore: PlaybackStore,
) {
    fun resolveMaxBitrate(quality: StreamingQuality): Long? {
        val net = networkOfflineStore.networkOffline.value

        // Check metered network behavior
        if (!isUnmeteredConnection()) {
            if (net.meteredNetworkBehavior == MeteredNetworkBehavior.BLOCK) {
                return 1L
            }
        }

        val resolved = when (quality) {
            StreamingQuality.AUTO -> when {
                // Adaptive disabled by the user → no dynamic cap, so the server
                // direct-plays whatever the device can decode (no transcode just
                // to hit a bitrate ceiling). Previously this fell back to a 720p
                // cap, which silently transcode high-bitrate direct-playable media.
                !net.adaptiveBitrateEnabled -> null
                isUnmeteredConnection() -> null
                else -> MAX_BITRATE_METERED
            }
            StreamingQuality.LOW_360P -> MAX_BITRATE_360P
            StreamingQuality.SD_480P -> MAX_BITRATE_480P
            StreamingQuality.HD_720P -> MAX_BITRATE_720P
            StreamingQuality.FHD_1080P -> MAX_BITRATE_1080P
            StreamingQuality.UHD_4K -> null
        }

        val cap = net.manualBandwidthCap
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

    /**
     * Resolves the effective max bitrate taking the active network type into
     * account. On a metered (cellular) connection the user's
     * [com.raulshma.jellyplay.core.model.legacy.UserPreferences.cellularStreamingQuality]
     * is used instead of the WiFi [com.raulshma.jellyplay.core.model.legacy.UserPreferences.streamingQuality].
     *
     * When [com.raulshma.jellyplay.core.model.legacy.UserPreferences.dataSaverEnabled]
     * is on, the result is additionally clamped to the data-saver ceiling
     * ([MAX_BITRATE_DATASAVER]) so the player never exceeds a frugal bitrate
     * regardless of the user-selected quality — this matches the "Data Saver"
     * toggle description (reduce data usage on streaming).
     */
    fun resolveEffectiveMaxBitrate(): Long? {
        val net = networkOfflineStore.networkOffline.value
        val pb = playbackStore.playback.value
        val quality = if (isUnmeteredConnection()) {
            pb.streamingQuality
        } else {
            pb.cellularStreamingQuality
        }
        val resolved = resolveMaxBitrate(quality)
        // Data saver forces an upper bound independent of the selected quality.
        return if (net.dataSaverEnabled) {
            val capped = MAX_BITRATE_DATASAVER
            if (resolved != null) kotlin.math.min(resolved, capped) else capped
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
        // Data-saver ceiling: targets ~480p to minimise mobile data usage
        // while keeping the stream watchable. Aligned with MAX_BITRATE_480P.
        private const val MAX_BITRATE_DATASAVER = 1_500_000L
    }
}
