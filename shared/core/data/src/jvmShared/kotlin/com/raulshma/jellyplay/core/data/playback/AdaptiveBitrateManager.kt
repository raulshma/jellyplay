package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality

/**
 * Moved from the legacy `:core:data` shim (playback-flips wave): the
 * `ConnectivityManager` ctor dep became the common [NetworkMonitor] seam, so
 * the manager is platform-free and Koin-owned
 * ([dataJvmModule][com.raulshma.jellyplay.core.data.di.dataJvmModule]
 * constructs it; the legacy DataModule bridges Hilt injectors to the single).
 *
 * ## Metered-parity with the legacy read
 *
 * The legacy `isUnmeteredConnection()` treated a null `activeNetwork` OR null
 * capabilities as *metered* (returned false). The Android [NetworkMonitor]
 * actual preserves exactly that: `currentMetered()` returns true (metered)
 * when `activeNetwork == null` and `deriveMeteredFromCapabilities` returns
 * true when capabilities are null — so `!isMetered.value` matches the legacy
 * semantics case for case. The desktop actual reports unmetered always
 * (no connectivity detection), which is the desktop assumption everywhere.
 */
class AdaptiveBitrateManager(
    private val networkMonitor: NetworkMonitor,
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

    fun isUnmeteredConnection(): Boolean = !networkMonitor.isMetered.value

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
