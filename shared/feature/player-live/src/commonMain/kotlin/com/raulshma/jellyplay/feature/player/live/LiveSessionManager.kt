package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackResolution
import com.raulshma.jellyplay.core.model.PlaybackResolveRequest
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback

private const val TAG = "LiveSessionManager"

/**
 * The live session's ONE source-resolution choreography — the role the VOD
 * player's `PlayerSessionManager.loadOnline` plays for video-on-demand.
 * The ViewModel keeps the uiState writes, the event emission and the engine
 * load; this class owns the client-side resolution policy over the
 * repository's one resolve seam ([PlaybackRepository.resolvePlayable]):
 *
 * 1. The resolve request — the server's full Direct Play / Direct Stream /
 *    Transcode decision tree under [PlaybackResolveRequest.liveStreamOption].
 * 2. The DIRECT_STREAM probe-override ([shouldIgnoreServerTranscodeVerdict]):
 *    when the user asked for Direct Stream but the server resolved a
 *    transcode, the live-source probe failed even though the tuner session
 *    is live — the verdict is ignored and a forced re-request runs instead.
 *    This policy stays HERE, client-side; the repository owns only the
 *    choreography.
 * 3. The forced re-request (`forceLiveStreamFallback`) makes the repository
 *    run its live ladder — fetch PlaybackInfo with
 *    `autoOpenLiveStream=true` semantics and a **blank** `mediaSourceId`
 *    (live sources have a server-generated source id distinct from the
 *    channel id; passing the channel id as the source id causes the server
 *    to return an empty source list), then build the URL via the
 *    liveStreamId arms. This is the ONE implementation of what used to be
 *    the inline ladder mirroring the VOD PlayerSessionManager.loadOnline
 *    fallback. The server has already opened the tuner session, so the URL
 *    works even when the source flags are all false.
 * 4. Returns null only when both paths fail — the caller surfaces the
 *    actual cause.
 *
 * The same entry serves the ordinary tune (`option` = the user's
 * [LiveStreamOption] preference) and the engine-requested transcode
 * fallback (`option` = TRANSCODE), so the fallback re-resolution re-runs
 * exactly the choreography the initial tune ran.
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
        val request = PlaybackResolveRequest(
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
        when (val verdict = playbackRepository.resolvePlayable(request)) {
            is PlaybackResolution.Resolved ->
                // DIRECT_STREAM probe-override policy (LiveStreamResolution,
                // pinned by LiveStreamResolutionTest): when the user asked
                // for Direct Stream but the server resolved a transcode, the
                // live-source probe failed even though the tuner session is
                // live — ignore the verdict and fall to the forced
                // re-request below; AUTO/TRANSCODE options accept the
                // server's pick.
                if (!shouldIgnoreServerTranscodeVerdict(option, verdict.playback.playMethod)) {
                    return verdict.playback
                } else {
                    Log.w(
                        TAG,
                        "Server resolved transcode for ${channel.name} despite " +
                            "DIRECT_STREAM request (probe failed); forcing direct stream"
                    )
                }
            PlaybackResolution.Unplayable, is PlaybackResolution.StaticFallback ->
                Log.w(TAG, "resolvePlayback returned null for ${channel.name} (option=$option); falling back to fetchPlaybackInfo")
        }

        // Forced re-request: the repository runs its live fallback ladder
        // (fetchPlaybackInfo → first source → liveStreamId URL) directly —
        // the former inline ladder whose comment conceded "Mirrors the VOD
        // PlayerSessionManager.loadOnline fallback" is one implementation
        // now.
        return when (val fallback = playbackRepository.resolvePlayable(
            request.copy(forceLiveStreamFallback = true),
        )) {
            is PlaybackResolution.Resolved -> fallback.playback
            else -> null
        }
    }
}
