package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackCaps
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.VideoPerformanceTuning

/**
 * The single profile-ceiling layer over the stream caps (issue #186 §5.7).
 * Folds the performance-profile tuning under the existing network/user caps —
 * the stricter value wins, nothing is ever raised:
 *
 * ```
 * userQualityCap = StreamingQuality.maxHeight of the active wifi/cellular tier
 * networkCap     = AdaptiveBitrateManager.resolveEffectiveMaxBitrate(serverHost)
 * profileCap     = VideoPerformanceMatrix.forTier(effectiveTier) caps
 *
 * effectiveBitrate = min(user/network cap, profile cap)
 * effectiveHeight  = min(user quality height, profile height)
 * ```
 *
 * [AdaptiveBitrateManager] stays untouched as the network/user ladder (§5.7:
 * "Keep it as the network/user ladder; the resolver is the single profile
 * layer") — this class only re-uses its public reads.
 *
 * ## The two entry points
 *
 *  - [forPersistedQuality] — the default path (`buildResolveRequest`,
 *    `reloadForStreamChange`, `switchMediaSource`): the bitrate comes from
 *    [AdaptiveBitrateManager.resolveEffectiveMaxBitrate] (home tier on a local
 *    host, data saver, metered ceiling, manual bandwidth all folded in), the
 *    height from the active wifi/cellular quality tier.
 *  - [forUserQuality] — the in-player quality reload (`reloadPlayback`): the
 *    user just picked an explicit quality, so the bitrate/height come from
 *    that quality's own ladder ([AdaptiveBitrateManager.resolveMaxBitrate]) —
 *    deliberately NOT the home-network tier, which is a persisted preference
 *    for the default path (§5.7 last paragraph: this preserves the existing
 *    `reloadPlayback` semantics). The `serverHost` parameter stays in the
 *    signature for call-site symmetry with [forPersistedQuality] but is
 *    intentionally unused here.
 */
class PlaybackCapResolver(
    private val adaptiveBitrateManager: AdaptiveBitrateManager,
    private val performanceResolver: VideoPerformanceResolver,
    private val playbackStore: PlaybackStore,
) {

    /**
     * Caps for paths that resolve the persisted quality (the home/metered
     * split). The bitrate rides the full [AdaptiveBitrateManager] fold; the
     * height rides the active wifi/cellular tier (the home tier affects the
     * bitrate only — matching `buildResolveRequest`'s current shape, §5.4's
     * "userQualityCap = the active wifi/cellular tier").
     */
    suspend fun forPersistedQuality(
        serverHost: String?,
        mode: PlaybackMode,
    ): PlaybackCaps {
        val playback = playbackStore.playback.value
        val quality = adaptiveBitrateManager.effectiveQuality(
            playback.streamingQuality,
            playback.cellularStreamingQuality,
        )
        val baseBitrate = adaptiveBitrateManager.resolveEffectiveMaxBitrate(serverHost)
        return applyProfileCeiling(
            baseBitrate = baseBitrate,
            baseHeight = quality.maxHeight,
            // The persisted profile passed explicitly — identical to resolve()'s
            // own default, but keeps the call on the one-arg method instead of
            // the compiler's $default bridge.
            tuning = performanceResolver.resolve(playback.videoPerformanceProfile).tuning,
            mode = mode,
        )
    }

    /**
     * Caps for paths where the user just chose an explicit quality in the
     * in-player sheet: the quality's own ladder (metered-AUTO ceiling, manual
     * bandwidth fold) + the profile ceiling. No home tier, no data saver —
     * the existing `reloadPlayback` semantics with the profile layered on.
     */
    suspend fun forUserQuality(
        quality: StreamingQuality,
        serverHost: String?,
        mode: PlaybackMode,
    ): PlaybackCaps = applyProfileCeiling(
        baseBitrate = adaptiveBitrateManager.resolveMaxBitrate(quality),
        baseHeight = quality.maxHeight,
        tuning = performanceResolver.resolve(
            playbackStore.playback.value.videoPerformanceProfile,
        ).tuning,
        mode = mode,
    )

    /**
     * The pure min-fold (§5.7), exposed for tests:
     *
     *  - `maxBitrate = minNullable(baseBitrate, tuning.maxVideoBitrate)`,
     *    `maxHeight` analogous;
     *  - `profileCapped*` true only when the PROFILE is the stricter value —
     *    a null base (uncapped user/network) counts as the profile winning,
     *    an exact tie does not;
     *  - `capsBypassed = mode == FORCE_DIRECT_PLAY` (the server may direct-
     *    play the original file regardless of the caps; the caps are still
     *    computed so the diagnostics can show what *would* have applied).
     */
    fun applyProfileCeiling(
        baseBitrate: Long?,
        baseHeight: Int?,
        tuning: VideoPerformanceTuning,
        mode: PlaybackMode,
    ): PlaybackCaps {
        // Locals first: the tuning properties are public API in another
        // module, so a `!= null` test cannot smart-cast them for the
        // comparisons below.
        val capBitrate = tuning.maxVideoBitrate
        val capHeight = tuning.maxVideoHeight
        return PlaybackCaps(
            maxBitrate = minNullable(baseBitrate, capBitrate),
            maxHeight = minNullable(baseHeight, capHeight),
            profileCappedBitrate = capBitrate != null &&
                (baseBitrate == null || capBitrate < baseBitrate),
            profileCappedHeight = capHeight != null &&
                (baseHeight == null || capHeight < baseHeight),
            capsBypassed = mode == PlaybackMode.FORCE_DIRECT_PLAY,
        )
    }

    private fun <T : Comparable<T>> minNullable(a: T?, b: T?): T? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }
}
