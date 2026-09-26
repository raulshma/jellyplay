package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback

private const val TAG = "LiveSessionManager"

/**
 * The live session's ONE source-resolution choreography — the role the VOD
 * player's `PlayerSessionManager.loadOnline` plays for video-on-demand,
 * extracted from the [LiveTvPlayerViewModel]'s former inline
 * `resolveLiveStream` (whose own comment conceded "Mirrors the VOD
 * PlayerSessionManager.loadOnline fallback"). The ViewModel keeps the
 * uiState writes, the event emission and the engine load; this class owns
 * the repository choreography that produces a playable [ResolvedPlayback]:
 *
 * 1. `resolvePlayback` — the full Direct Play / Direct Stream / Transcode
 *    decision tree; returns null only when the server offers no playable
 *    method.
 * 2. The DIRECT_STREAM probe-override ([shouldIgnoreServerTranscodeVerdict]):
 *    when the user asked for Direct Stream but the server resolved a
 *    transcode, the live-source probe failed even though the tuner session
 *    is live — the verdict is ignored and the ladder below runs instead.
 * 3. The fetchPlaybackInfo/liveStreamId ladder
 *    ([resolveLiveStreamResolution]): fetch PlaybackInfo with
 *    `autoOpenLiveStream=true` semantics and a **blank** `mediaSourceId`
 *    (live sources have a server-generated source id distinct from the
 *    channel id; passing the channel id as the source id causes the server
 *    to return an empty source list), then build the URL via
 *    `getStreamUrl(liveStreamId)`. Mirrors the VOD
 *    PlayerSessionManager.loadOnline fallback. The server has already
 *    opened the tuner session, so the URL works even when the source flags
 *    are all false.
 * 4. Returns null only when both paths fail — the caller surfaces the
 *    actual cause.
 *
 * The same entry serves the ordinary tune (`option` = the user's
 * [LiveStreamOption] preference) and the engine-requested transcode
 * fallback (`option` = TRANSCODE), so the fallback re-resolution re-runs
 * exactly the choreography the initial tune ran — the former inline
 * duplicate cannot drift anymore.
 *
 * Internal: consumed only by the ViewModel and this module's jvmTest —
 * not a stable API surface (the LiveMuteMemory / LiveStreamResolution
 * seam shape).
 */
internal class LiveSessionManager(
    private val playbackRepository: PlaybackRepository,
) {
    /**
     * Resolves a playable live URL for [channel] under [option], carrying the
     * route's [audioStreamIndex]/[subtitleStreamIndex] overrides through every
     * repository call. Returns null only when neither the decision tree nor
     * the fallback ladder yields a URL.
     */
    suspend fun resolve(
        channel: LiveTvChannel,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        option: LiveStreamOption,
        playerType: PlayerType,
    ): ResolvedPlayback? {
        // Pass mediaSourceId = "" so the server does not filter on a
        // channel-id-as-source-id. mode = AUTO is inert here because the
        // live flag table is driven by `liveStreamOption`.
        val resolved = playbackRepository.resolvePlayback(
            itemId = channel.id,
            mediaSourceId = "",
            startTimeTicks = 0L,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxStreamingBitrateBits = null,
            mode = PlaybackMode.AUTO,
            playerType = playerType,
            liveStreamOption = option,
        )
        if (resolved != null) {
            // DIRECT_STREAM probe-override policy (LiveStreamResolution,
            // pinned by LiveStreamResolutionTest): when the user asked for
            // Direct Stream but the server resolved a transcode, the
            // live-source probe failed even though the tuner session is
            // live — ignore the verdict and fall to the liveStreamId ladder
            // below; AUTO/TRANSCODE options accept the server's pick.
            if (shouldIgnoreServerTranscodeVerdict(option, resolved.playMethod)) {
                Log.w(
                    TAG,
                    "Server resolved transcode for ${channel.name} despite " +
                        "DIRECT_STREAM request (probe failed); forcing direct stream"
                )
            } else {
                return resolved
            }
        } else {
            Log.w(TAG, "resolvePlayback returned null for ${channel.name} (option=$option); falling back to fetchPlaybackInfo")
        }

        // Fallback: fetch PlaybackInfo directly and build a direct stream URL
        // from the first source's liveStreamId. Mirrors the VOD
        // PlayerSessionManager.loadOnline fallback.
        val info: PlaybackInfoResult = playbackRepository
            .fetchPlaybackInfo(
                itemId = channel.id,
                mediaSourceId = "",
                startTimeTicks = 0L,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                maxStreamingBitrateBits = null,
                mode = PlaybackMode.AUTO,
                playerType = playerType,
                liveStreamOption = option,
            )
            .getOrNull() ?: run {
            Log.e(TAG, "fetchPlaybackInfo failed for ${channel.name}")
            return null
        }

        val source = info.mediaSources.firstOrNull() ?: run {
            Log.e(TAG, "fetchPlaybackInfo returned no media sources for ${channel.name}")
            return null
        }
        Log.i(
            TAG,
            "Source for ${channel.name}: id=${source.id}, " +
                "directPlay=${source.supportsDirectPlay}, " +
                "directStream=${source.supportsDirectStream}, " +
                "transcode=${source.supportsTranscoding}, " +
                "transcodeUrl=${source.transcodeUrl != null}, " +
                "liveStreamId=${source.liveStreamId != null}, " +
                "requiresOpening=${source.requiresOpening}"
        )

        // Pure capability ladder + play-method fold (LiveStreamResolution,
        // pinned by LiveStreamResolutionTest); this manager keeps only the
        // repo URL call (injected as the builder) and the logging.
        val resolution = resolveLiveStreamResolution(
            source = source,
            buildStreamUrl = { mediaSourceId, liveStreamId ->
                playbackRepository.getStreamUrl(
                    itemId = channel.id,
                    mediaSourceId = mediaSourceId,
                    startTimeTicks = 0L,
                    liveStreamId = liveStreamId,
                )
            },
        )
        val stream = when (resolution) {
            is LiveStreamResolution.Resolved -> resolution
            LiveStreamResolution.NoPlayableMethod -> {
                Log.e(TAG, "No playable method offered for ${channel.name}")
                return null
            }
        }
        if (stream.via == LiveStreamResolution.Via.LIVE_STREAM_ID) {
            Log.w(TAG, "All playability flags false for ${channel.name}; attempting direct stream via liveStreamId")
        }
        if (stream.url.isBlank()) {
            Log.e(TAG, "Resolved URL is blank for ${channel.name}")
            return null
        }
        return ResolvedPlayback(
            mediaSourceId = source.id,
            streamUrl = stream.url,
            playMethod = stream.playMethod,
            playSessionId = info.playSessionId,
            maxStreamingBitrate = null,
            container = source.container,
        )
    }
}
